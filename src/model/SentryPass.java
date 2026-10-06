package model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SentryPass implements Serializable {

    public enum PassStatus { ACTIVE, USED, CANCELLED, EXPIRED }

    private String passId;
    private int bookingId;
    private int userId;
    private String userName;
    private int eventId;
    private String eventName;
    private String eventDate;
    private String eventTime;
    private String venue;
    private String seatNumbers;
    private double amountPaid;
    private LocalDateTime issuedAt;
    private LocalDateTime validUntil;
    private PassStatus passStatus;
    private String qrCode;
    private boolean checkedIn;
    private LocalDateTime checkInTime;

    public SentryPass(String passId, int bookingId, int userId, String userName, int eventId, String eventName,
                      String eventDate, String eventTime, String venue, String seatNumbers, double amountPaid,
                      LocalDateTime validUntil, String qrCode) {
        this.passId = passId;
        this.bookingId = bookingId;
        this.userId = userId;
        this.userName = userName;
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.seatNumbers = seatNumbers;
        this.amountPaid = amountPaid;
        this.issuedAt = LocalDateTime.now();
        this.validUntil = validUntil;
        this.passStatus = PassStatus.ACTIVE;
        this.qrCode = qrCode;
        this.checkedIn = false;
        this.checkInTime = null;
    }

    // Constructor for reconstructing from file
    public SentryPass(String passId, int bookingId, int userId, String userName, int eventId, String eventName,
                      String eventDate, String eventTime, String venue, String seatNumbers, double amountPaid,
                      LocalDateTime issuedAt, LocalDateTime validUntil, PassStatus passStatus, String qrCode,
                      boolean checkedIn, LocalDateTime checkInTime) {
        this.passId = passId;
        this.bookingId = bookingId;
        this.userId = userId;
        this.userName = userName;
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.venue = venue;
        this.seatNumbers = seatNumbers;
        this.amountPaid = amountPaid;
        this.issuedAt = issuedAt;
        this.validUntil = validUntil;
        this.passStatus = passStatus;
        this.qrCode = qrCode;
        this.checkedIn = checkedIn;
        this.checkInTime = checkInTime;
    }

    public String getPassId() { return passId; }
    public int getBookingId() { return bookingId; }
    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public int getEventId() { return eventId; }
    public String getEventName() { return eventName; }
    public String getEventDate() { return eventDate; }
    public String getEventTime() { return eventTime; }
    public String getVenue() { return venue; }
    public String getSeatNumbers() { return seatNumbers; }
    public double getAmountPaid() { return amountPaid; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public PassStatus getPassStatus() { return passStatus; }
    public void setPassStatus(PassStatus passStatus) { this.passStatus = passStatus; }
    public String getQrCode() { return qrCode; }
    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }
    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }

    public String toFileLine() {
        return passId + "|" + bookingId + "|" + userId + "|" + userName + "|" + eventId + "|" + eventName + "|" +
               eventDate + "|" + eventTime + "|" + venue + "|" + seatNumbers + "|" + amountPaid + "|" +
               issuedAt + "|" + validUntil + "|" + passStatus + "|" + qrCode + "|" + checkedIn + "|" + checkInTime;
    }
}
