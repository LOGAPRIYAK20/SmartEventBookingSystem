package exception;

public class DuplicateCheckInException extends Exception {
    public DuplicateCheckInException(String message) {
        super(message);
    }
}
