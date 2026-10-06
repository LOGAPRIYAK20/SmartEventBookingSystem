package exception;

/**
 * Thrown when a payment attempt fails validation or processing.
 */
public class PaymentFailureException extends Exception {
    public PaymentFailureException(String message) {
        super(message);
    }
}
