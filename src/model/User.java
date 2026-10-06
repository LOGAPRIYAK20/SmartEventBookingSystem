package model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered user of the system.
 * Demonstrates: Encapsulation (private fields + getters/setters),
 * Constructors, and forms the base class for Admin (Inheritance).
 */
public class User implements Serializable {
    private static int idCounter = 1000;

    private final int userId;
    private String name;
    private String email;
    private String password; // stored as a simple hash for demo purposes
    private String city;
    private String preferredCategory;
    private int loyaltyPoints;
    private final List<Integer> bookingHistoryIds; // stores Booking IDs

    public User(String name, String email, String password, String city, String preferredCategory) {
        this.userId = idCounter++;
        this.name = name;
        this.email = email;
        this.password = hash(password);
        this.city = city;
        this.preferredCategory = preferredCategory;
        this.loyaltyPoints = 0;
        this.bookingHistoryIds = new ArrayList<>();
    }

    // Constructor used when reloading a user from file storage (preserves ID)
    public User(int userId, String name, String email, String hashedPassword,
                String city, String preferredCategory, int loyaltyPoints) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.password = hashedPassword;
        this.city = city;
        this.preferredCategory = preferredCategory;
        this.loyaltyPoints = loyaltyPoints;
        this.bookingHistoryIds = new ArrayList<>();
        if (userId >= idCounter) {
            idCounter = userId + 1;
        }
    }

    // Simple, deterministic "hash" so passwords aren't stored as plain text in the demo.
    // NOTE: For a production system use a real algorithm like BCrypt.
    public static String hash(String raw) {
        return Integer.toHexString(raw.hashCode());
    }

    public boolean checkPassword(String rawPassword) {
        return this.password.equals(hash(rawPassword));
    }

    public void addBookingId(int bookingId) {
        bookingHistoryIds.add(bookingId);
    }

    public void addLoyaltyPoints(int points) {
        this.loyaltyPoints += points;
    }

    public boolean redeemLoyaltyPoints(int points) {
        if (points > loyaltyPoints) return false;
        loyaltyPoints -= points;
        return true;
    }

    // ---------- Getters / Setters (Encapsulation) ----------
    public int getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getHashedPassword() { return password; }
    public void setPassword(String rawPassword) { this.password = hash(rawPassword); }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPreferredCategory() { return preferredCategory; }
    public void setPreferredCategory(String preferredCategory) { this.preferredCategory = preferredCategory; }
    public int getLoyaltyPoints() { return loyaltyPoints; }
    public List<Integer> getBookingHistoryIds() { return bookingHistoryIds; }

    public String role() {
        return "USER";
    }

    @Override
    public String toString() {
        return String.format("[%d] %s <%s> | City: %s | Preferred: %s | Points: %d | Role: %s",
                userId, name, email, city, preferredCategory, loyaltyPoints, role());
    }

    // Serialized as pipe-delimited line for simple file storage
    public String toFileLine() {
        return userId + "|" + name + "|" + email + "|" + password + "|" + city + "|" +
                preferredCategory + "|" + loyaltyPoints + "|" + role();
    }
}
