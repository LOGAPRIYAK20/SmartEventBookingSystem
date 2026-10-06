package exception;

/**
 * Thrown when user-supplied input fails validation
 * (empty fields, bad email format, out-of-range numbers, etc.)
 */
public class InvalidInputException extends Exception {
    public InvalidInputException(String message) {
        super(message);
    }
}
