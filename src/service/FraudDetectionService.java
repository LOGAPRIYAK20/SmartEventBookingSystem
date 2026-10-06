package service;

import model.Booking;
import repository.BookingRepository;
import util.FileManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Flags suspicious activity for admin review:
 *  - A single booking of more than MAX_SEATS_PER_TRANSACTION seats.
 *  - More than MAX_BOOKINGS_IN_WINDOW bookings by the same user within WINDOW_MINUTES.
 */
public class FraudDetectionService {

    private static final int MAX_SEATS_PER_TRANSACTION = 6;
    private static final int MAX_BOOKINGS_IN_WINDOW = 3;
    private static final int WINDOW_MINUTES = 10;

    private final BookingRepository bookingRepository;

    public FraudDetectionService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public boolean isSuspicious(int userId, int seatsRequested) {
        if (seatsRequested > MAX_SEATS_PER_TRANSACTION) {
            flag("User " + userId + " requested " + seatsRequested + " seats in one transaction.");
            return true;
        }

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        long recentCount = 0;
        List<Booking> userBookings = bookingRepository.findByUser(userId);
        for (Booking b : userBookings) {
            if (ChronoUnit.MINUTES.between(b.getBookingTime(), LocalDateTime.now()) <= WINDOW_MINUTES
                    && b.getBookingTime().isAfter(windowStart)) {
                recentCount++;
            }
        }
        if (recentCount >= MAX_BOOKINGS_IN_WINDOW) {
            flag("User " + userId + " made " + recentCount + " bookings within " + WINDOW_MINUTES + " minutes.");
            return true;
        }
        return false;
    }

    private void flag(String reason) {
        FileManager.log("FRAUD ALERT: " + reason);
    }
}
