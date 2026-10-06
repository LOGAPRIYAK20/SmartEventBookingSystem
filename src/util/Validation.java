package util;

import exception.InvalidInputException;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Centralised input validation so every module (registration, booking,
 * payment, event creation) checks input consistently.
 */
public class Validation {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    public static void requireNonEmpty(String value, String fieldName) throws InvalidInputException {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidInputException(fieldName + " cannot be empty.");
        }
    }

    public static void validateEmail(String email) throws InvalidInputException {
        requireNonEmpty(email, "Email");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidInputException("Invalid email format: " + email);
        }
    }

    public static void validatePassword(String password) throws InvalidInputException {
        if (password == null || password.length() < 4) {
            throw new InvalidInputException("Password must be at least 4 characters long.");
        }
    }

    public static int parsePositiveInt(String value, String fieldName) throws InvalidInputException {
        try {
            int n = Integer.parseInt(value.trim());
            if (n <= 0) throw new InvalidInputException(fieldName + " must be a positive number.");
            return n;
        } catch (NumberFormatException e) {
            throw new InvalidInputException(fieldName + " must be a valid whole number.");
        }
    }

    public static double parsePositiveDouble(String value, String fieldName) throws InvalidInputException {
        try {
            double n = Double.parseDouble(value.trim());
            if (n <= 0) throw new InvalidInputException(fieldName + " must be a positive number.");
            return n;
        } catch (NumberFormatException e) {
            throw new InvalidInputException(fieldName + " must be a valid number.");
        }
    }

    public static LocalDate parseFutureOrTodayDate(String value, String fieldName) throws InvalidInputException {
        try {
            LocalDate date = LocalDate.parse(value.trim());
            return date;
        } catch (Exception e) {
            throw new InvalidInputException(fieldName + " must be in YYYY-MM-DD format.");
        }
    }
}
