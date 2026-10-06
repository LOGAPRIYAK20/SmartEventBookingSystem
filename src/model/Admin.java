package model;

/**
 * Admin extends User (Inheritance) and gets extended privileges
 * such as managing events and viewing analytics.
 * Overrides role() -> Polymorphism (method overriding).
 */
public class Admin extends User {

    public Admin(String name, String email, String password, String city, String preferredCategory) {
        super(name, email, password, city, preferredCategory);
    }

    public Admin(int userId, String name, String email, String hashedPassword,
                 String city, String preferredCategory, int loyaltyPoints) {
        super(userId, name, email, hashedPassword, city, preferredCategory, loyaltyPoints);
    }

    @Override
    public String role() {
        return "ADMIN";
    }
}
