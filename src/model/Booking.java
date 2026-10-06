package model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents a single ticket booking made by a user for an event.
 */
public class Booking implements Serializable {

    public enum Status { CONFIRMED, CANCELLED, WAITLISTED }

    private static int idCounter = 5000;

    private final int bookingId;
    private final int userId;
    private final int eventId;
    private int seatCount;
    private String seatLabels;      // e.g. "A1,A2,A3"
    private double pricePaid;
    private Status status;
    private final LocalDateTime bookingTime;
    private String qrCode;

    public Booking(int userId, int eventId, int seatCount, String seatLabels,
                    double pricePaid, Status status) {
        this.bookingId = idCounter++;
        this.userId = userId;
        this.eventId = eventId;
        this.seatCount = seatCount;
        this.seatLabels = seatLabels;
        this.pricePaid = pricePaid;
        this.status = status;
        this.bookingTime = LocalDateTime.now();
    }

    public Booking(int bookingId, int userId, int eventId, int seatCount, String seatLabels,
                    double pricePaid, Status status, LocalDateTime bookingTime, String qrCode) {
        this.bookingId = bookingId;
        this.userId = userId;
        this.eventId = eventId;
        this.seatCount = seatCount;
        this.seatLabels = seatLabels;
        this.pricePaid = pricePaid;
        this.status = status;
        this.bookingTime = bookingTime;
        this.qrCode = qrCode;
        if (bookingId >= idCounter) idCounter = bookingId + 1;
    }

    // ---------- Getters / Setters ----------
    public int getBookingId() { return bookingId; }
    public int getUserId() { return userId; }
    public int getEventId() { return eventId; }
    public int getSeatCount() { return seatCount; }
    public String getSeatLabels() { return seatLabels; }
    public void setSeatLabels(String seatLabels) { this.seatLabels = seatLabels; }
    public double getPricePaid() { return pricePaid; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getBookingTime() { return bookingTime; }
    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    @Override
    public String toString() {
        return String.format("Booking#%d | User:%d | Event:%d | Seats:%s | Paid:Rs.%.2f | %s | %s",
                bookingId, userId, eventId, seatLabels, pricePaid, status, bookingTime);
    }

    public String toFileLine() {
        return bookingId + "|" + userId + "|" + eventId + "|" + seatCount + "|" + seatLabels + "|" +
                pricePaid + "|" + status + "|" + bookingTime + "|" + qrCode;
    }
}
