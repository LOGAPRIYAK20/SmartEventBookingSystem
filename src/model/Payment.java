package model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Represents a payment transaction linked to a Booking.
 */
public class Payment implements Serializable {
    private static int idCounter = 9000;

    public enum PaymentStatus { SUCCESS, FAILED, REFUNDED }

    private final int paymentId;
    private final int bookingId;
    private final double amount;
    private final String method; // e.g. "CARD", "UPI"
    private PaymentStatus status;
    private final LocalDateTime timestamp;

    public Payment(int bookingId, double amount, String method, PaymentStatus status) {
        this.paymentId = idCounter++;
        this.bookingId = bookingId;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public int getPaymentId() { return paymentId; }
    public int getBookingId() { return bookingId; }
    public double getAmount() { return amount; }
    public String getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("Payment#%d | Booking:%d | Rs.%.2f via %s | %s | %s",
                paymentId, bookingId, amount, method, status, timestamp);
    }

    public String toFileLine() {
        return paymentId + "|" + bookingId + "|" + amount + "|" + method + "|" + status + "|" + timestamp;
    }
}
