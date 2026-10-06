package service;

import exception.DuplicateCheckInException;
import exception.InvalidSentryPassException;
import model.Booking;
import model.Event;
import model.SentryPass;
import model.User;
import repository.SentryPassRepository;
import util.FileManager;
import util.QRGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SentryPassService {

    private final SentryPassRepository sentryPassRepository;

    public SentryPassService(SentryPassRepository sentryPassRepository) {
        this.sentryPassRepository = sentryPassRepository;
    }

    public SentryPass generatePass(Booking booking, User user, Event event) {
        String passId = "SP-" + LocalDateTime.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        
        String qrData = String.format("PASS:%s|BKG:%d|USR:%d|EVT:%d", passId, booking.getBookingId(), user.getUserId(), event.getEventId());
        String qrCode = QRGenerator.generateQR(qrData);

        LocalDateTime validUntil = event.getDate().plusDays(1).atStartOfDay();

        SentryPass pass = new SentryPass(
                passId, booking.getBookingId(), user.getUserId(), user.getName(), event.getEventId(),
                event.getName(), event.getDate().toString(),
                "18:00", event.getLocation(),
                booking.getSeatLabels(), booking.getPricePaid(), validUntil, qrCode
        );

        sentryPassRepository.addPass(pass);
        FileManager.log("SENTRY_PASS_GENERATED: " + passId + " for Booking " + booking.getBookingId());

        return pass;
    }
    
    public SentryPass generatePass(Booking booking, User user, Event event, String specialMessage) {
        SentryPass pass = generatePass(booking, user, event);
        FileManager.log("SENTRY_PASS_GENERATED (Special: " + specialMessage + "): " + pass.getPassId());
        return pass;
    }

    public SentryPass validateAndCheckIn(String passId) throws InvalidSentryPassException, DuplicateCheckInException {
        Optional<SentryPass> optionalPass = sentryPassRepository.findById(passId);
        
        if (optionalPass.isEmpty()) {
            FileManager.log("ENTRY_DENIED: Pass not found " + passId);
            throw new InvalidSentryPassException("Sentry Pass not found.");
        }
        
        SentryPass pass = optionalPass.get();
        
        if (pass.getPassStatus() == SentryPass.PassStatus.CANCELLED) {
            FileManager.log("ENTRY_DENIED: Cancelled pass " + passId);
            throw new InvalidSentryPassException("This Sentry Pass has been cancelled.");
        }
        
        if (pass.getPassStatus() == SentryPass.PassStatus.EXPIRED || LocalDateTime.now().isAfter(pass.getValidUntil())) {
            pass.setPassStatus(SentryPass.PassStatus.EXPIRED);
            sentryPassRepository.saveAll();
            FileManager.log("ENTRY_DENIED: Expired pass " + passId);
            throw new InvalidSentryPassException("This Sentry Pass has expired.");
        }
        
        if (pass.getPassStatus() == SentryPass.PassStatus.USED || pass.isCheckedIn()) {
            FileManager.log("ENTRY_DENIED: Duplicate check-in " + passId);
            throw new DuplicateCheckInException("This Sentry Pass has already been used.");
        }
        
        // Validation successful, update status
        pass.setPassStatus(SentryPass.PassStatus.USED);
        pass.setCheckedIn(true);
        pass.setCheckInTime(LocalDateTime.now());
        sentryPassRepository.saveAll();
        
        FileManager.log("ENTRY_APPROVED: " + passId);
        return pass;
    }

    public List<SentryPass> getUserPasses(int userId) {
        return sentryPassRepository.findByUserId(userId);
    }

    public List<SentryPass> getAllPasses() {
        return sentryPassRepository.findAll();
    }
}
