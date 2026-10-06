package service;

import exception.PaymentFailureException;
import exception.SeatUnavailableException;
import model.*;
import repository.BookingRepository;
import repository.EventRepository;
import repository.UserRepository;
import util.FileManager;
import util.QRGenerator;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full booking lifecycle: pricing -> seat locking ->
 * payment -> confirmation/QR ticket -> loyalty points, and the
 * cancellation -> refund -> waitlist auto-confirmation lifecycle.
 */
public class BookingService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    private final PricingStrategy pricingStrategy = new DynamicPricingStrategy();
    private final SeatRecommendationService seatRecommendationService = new SeatRecommendationService();
    private final SeatLockManager seatLockManager;
    private final WaitlistService waitlistService;
    private final FraudDetectionService fraudDetectionService;
    private final RefundService refundService = new RefundService();
    private final LoyaltyService loyaltyService = new LoyaltyService();

    public BookingService(BookingRepository bookingRepository, EventRepository eventRepository,
                           UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.seatLockManager = new SeatLockManager(eventRepository);
        this.waitlistService = new WaitlistService(bookingRepository, eventRepository);
        this.fraudDetectionService = new FraudDetectionService(bookingRepository);
    }

    /**
     * Books tickets end-to-end. Returns the confirmed Booking, or a
     * WAITLISTED booking if seats ran out, or throws on payment failure.
     */
    public Booking bookTicket(User user, Event event, int seatsRequested,
                               PaymentMethod paymentMethod, String paymentCredential)
            throws SeatUnavailableException, PaymentFailureException {

        if (fraudDetectionService.isSuspicious(user.getUserId(), seatsRequested)) {
            FileManager.log("Booking flagged for admin review: user=" + user.getUserId());
        }

        List<String> suggestedSeats = seatRecommendationService.recommendSeats(event, seatsRequested);
        String seatLabels = String.join(",", suggestedSeats);

        if (event.getAvailableSeats() < seatsRequested) {
            // No seats left -> join waitlist instead of failing outright
            Booking waitlisted = new Booking(user.getUserId(), event.getEventId(), seatsRequested,
                    seatLabels, 0, Booking.Status.WAITLISTED);
            bookingRepository.save(waitlisted);
            user.addBookingId(waitlisted.getBookingId());
            userRepository.save(user);
            waitlistService.addToWaitlist(event.getEventId(), waitlisted.getBookingId());
            FileManager.log("Event " + event.getEventId() + " sold out - user " + user.getUserId() + " waitlisted.");
            return waitlisted;
        }

        double price = pricingStrategy.calculatePrice(event, seatsRequested);

        if (!seatLockManager.lockSeats(event.getEventId(), user.getUserId(), seatsRequested)) {
            throw new SeatUnavailableException("Seats became unavailable while processing your request.");
        }

        try {
            paymentMethod.pay(price, paymentCredential); // throws PaymentFailureException on failure
        } catch (PaymentFailureException ex) {
            seatLockManager.releaseLock(event.getEventId(), user.getUserId(), seatsRequested);
            throw ex;
        }

        seatLockManager.confirmLock(event.getEventId(), user.getUserId());
        eventRepository.save(event); // seats already decremented by the lock manager

        Booking booking = new Booking(user.getUserId(), event.getEventId(), seatsRequested,
                seatLabels, price, Booking.Status.CONFIRMED);
        booking.setQrCode(QRGenerator.generateCode(booking.getBookingId(), event.getEventId(), user.getUserId()));
        bookingRepository.save(booking);

        user.addBookingId(booking.getBookingId());
        user.addLoyaltyPoints(loyaltyService.pointsEarned(price));
        userRepository.save(user);

        FileManager.backup("BOOKING_CONFIRMED " + booking.toFileLine());
        FileManager.log("Booking #" + booking.getBookingId() + " confirmed for user " + user.getUserId());

        return booking;
    }

    public double cancelTicket(Booking booking) {
        Event event = eventRepository.findById(booking.getEventId()).orElse(null);
        double refundAmount = 0;

        if (booking.getStatus() == Booking.Status.CONFIRMED && event != null) {
            refundAmount = refundService.calculateRefundAmount(event, booking.getPricePaid());
            event.releaseSeats(booking.getSeatCount());
            eventRepository.save(event);
        }

        booking.setStatus(Booking.Status.CANCELLED);
        bookingRepository.save(booking);

        FileManager.backup("BOOKING_CANCELLED booking=" + booking.getBookingId() + " refund=" + refundAmount);
        FileManager.log("Booking #" + booking.getBookingId() + " cancelled. Refund: Rs." + refundAmount);

        if (event != null) {
            waitlistService.tryAutoConfirm(event.getEventId()); // free seat -> offer to next in line
        }
        return refundAmount;
    }

    public Optional<Booking> findBooking(int bookingId) {
        return bookingRepository.findById(bookingId);
    }

    public List<Booking> historyForUser(int userId) {
        return bookingRepository.findByUser(userId);
    }

    public int waitlistPosition(int eventId, int bookingId) {
        return waitlistService.waitlistPosition(eventId, bookingId);
    }

    public void shutdown() {
        seatLockManager.shutdown();
    }
}
