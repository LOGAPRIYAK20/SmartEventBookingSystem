package exception;

/**
 * Thrown when a requested seat/ticket count cannot be satisfied
 * for an event because not enough seats are available.
 */
public class SeatUnavailableException extends Exception {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
