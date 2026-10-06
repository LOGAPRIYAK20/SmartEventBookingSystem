package menu;

import exception.InvalidInputException;
import exception.PaymentFailureException;
import exception.SeatUnavailableException;
import model.*;
import repository.BookingRepository;
import repository.EventRepository;
import repository.UserRepository;
import service.*;
import util.QRGenerator;
import util.Validation;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

/**
 * Menu-driven console controller. Keeps all System.in/out interaction
 * in one place so the service/repository layers stay UI-agnostic.
 */
public class Menu {

    private final Scanner sc = new Scanner(System.in);

    private final UserRepository userRepository = new UserRepository();
    private final EventRepository eventRepository = new EventRepository();
    private final BookingRepository bookingRepository = new BookingRepository();

    private final BookingService bookingService =
            new BookingService(bookingRepository, eventRepository, userRepository);
    private final RecommendationService recommendationService =
            new RecommendationService(eventRepository, bookingRepository);
    private final AnalyticsService analyticsService =
            new AnalyticsService(bookingRepository, eventRepository);

    private User currentUser;

    public void start() {
        seedAdminIfMissing();
        System.out.println("=====================================================");
        System.out.println(" SMART EVENT TICKET BOOKING & MANAGEMENT SYSTEM");
        System.out.println("=====================================================");

        boolean running = true;
        while (running) {
            if (currentUser == null) {
                running = authGate();
            } else if (currentUser instanceof Admin) {
                adminMenu();
            } else {
                userMenu();
            }
        }
        bookingService.shutdown();
        System.out.println("Thank you for using the Smart Event Booking System. Goodbye!");
    }

    // ---------------------------------------------------------------
    // AUTH
    // ---------------------------------------------------------------
    private boolean authGate() {
        System.out.println("\n1. Login  2. Register  0. Exit");
        System.out.print("Choose: ");
        String choice = sc.nextLine().trim();
        switch (choice) {
            case "1": login(); return true;
            case "2": register(); return true;
            case "0": return false;
            default: System.out.println("Invalid choice."); return true;
        }
    }

    private void login() {
        System.out.print("Email: ");
        String email = sc.nextLine().trim();
        System.out.print("Password: ");
        String password = sc.nextLine().trim();

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().checkPassword(password)) {
            currentUser = userOpt.get();
            System.out.println("Welcome back, " + currentUser.getName() + "! (" + currentUser.role() + ")");
        } else {
            System.out.println("Invalid email or password.");
        }
    }

    private void register() {
        try {
            System.out.print("Name: ");
            String name = sc.nextLine().trim();
            Validation.requireNonEmpty(name, "Name");

            System.out.print("Email: ");
            String email = sc.nextLine().trim();
            Validation.validateEmail(email);
            if (userRepository.emailExists(email)) {
                System.out.println("An account with this email already exists.");
                return;
            }

            System.out.print("Password (min 4 chars): ");
            String password = sc.nextLine().trim();
            Validation.validatePassword(password);

            System.out.print("City: ");
            String city = sc.nextLine().trim();
            Validation.requireNonEmpty(city, "City");

            System.out.print("Preferred category (e.g. Music, Sports, Tech): ");
            String category = sc.nextLine().trim();
            Validation.requireNonEmpty(category, "Preferred category");

            User user = new User(name, email, password, city, category);
            userRepository.save(user);
            System.out.println("Registration successful! Your User ID is " + user.getUserId() + ". Please log in.");
        } catch (InvalidInputException e) {
            System.out.println("Registration failed: " + e.getMessage());
        }
    }

    private void seedAdminIfMissing() {
        if (userRepository.findByEmail("admin@events.com").isEmpty()) {
            Admin admin = new Admin("System Admin", "admin@events.com", "admin123", "HQ", "General");
            userRepository.save(admin);
            System.out.println("(First run) Default admin created -> admin@events.com / admin123");
        }
    }

    // ---------------------------------------------------------------
    // USER MENU
    // ---------------------------------------------------------------
    private void userMenu() {
        System.out.println("\n--- USER MENU (" + currentUser.getName() + ") ---");
        System.out.println("1. Browse All Events");
        System.out.println("2. Search Events");
        System.out.println("3. Recommended For You");
        System.out.println("4. Book Tickets");
        System.out.println("5. My Booking History");
        System.out.println("6. Cancel a Booking");
        System.out.println("7. View Digital Ticket (QR)");
        System.out.println("8. My Profile / Loyalty Points");
        System.out.println("9. Update Profile");
        System.out.println("0. Logout");
        System.out.print("Choose: ");
        String choice = sc.nextLine().trim();

        switch (choice) {
            case "1": listEvents(eventRepository.findAll()); break;
            case "2": searchEvents(); break;
            case "3": listEvents(recommendationService.recommendFor(currentUser)); break;
            case "4": bookTicketsFlow(); break;
            case "5": bookingHistory(); break;
            case "6": cancelBookingFlow(); break;
            case "7": viewTicket(); break;
            case "8": System.out.println(currentUser); break;
            case "9": updateProfile(); break;
            case "0": currentUser = null; break;
            default: System.out.println("Invalid choice.");
        }
    }

    private void listEvents(List<Event> events) {
        if (events.isEmpty()) {
            System.out.println("No events to show.");
            return;
        }
        System.out.println("\n----- EVENTS -----");
        for (Event e : events) System.out.println(e);
    }

    private void searchEvents() {
        System.out.print("Enter keyword (name/location/category/date): ");
        String keyword = sc.nextLine().trim();
        listEvents(eventRepository.search(keyword));
    }

    private void bookTicketsFlow() {
        try {
            System.out.print("Enter Event ID: ");
            int eventId = Validation.parsePositiveInt(sc.nextLine(), "Event ID");
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new InvalidInputException("No event found with ID " + eventId));

            System.out.print("Number of seats: ");
            int seats = Validation.parsePositiveInt(sc.nextLine(), "Seat count");

            System.out.print("Payment method (1=Card, 2=UPI): ");
            String pm = sc.nextLine().trim();
            PaymentMethod method = pm.equals("2") ? new UpiPayment() : new CardPayment();

            System.out.print(method.methodName().equals("CARD") ? "Card number (16 digits): " : "UPI ID (name@bank): ");
            String credential = sc.nextLine().trim();

            Booking booking = bookingService.bookTicket(currentUser, event, seats, method, credential);

            if (booking.getStatus() == Booking.Status.WAITLISTED) {
                int pos = bookingService.waitlistPosition(eventId, booking.getBookingId());
                System.out.println("Event is sold out. You've been added to the waitlist at position " + pos + ".");
            } else {
                System.out.println("Booking confirmed! " + booking);
                System.out.println(QRGenerator.renderAscii(booking.getQrCode()));
            }
        } catch (InvalidInputException | SeatUnavailableException | PaymentFailureException e) {
            System.out.println("Booking failed: " + e.getMessage());
        }
    }

    private void bookingHistory() {
        List<Booking> history = bookingService.historyForUser(currentUser.getUserId());
        if (history.isEmpty()) {
            System.out.println("You have no bookings yet.");
            return;
        }
        System.out.println("\n----- YOUR BOOKINGS -----");
        for (Booking b : history) System.out.println(b);
    }

    private void cancelBookingFlow() {
        try {
            System.out.print("Enter Booking ID to cancel: ");
            int id = Validation.parsePositiveInt(sc.nextLine(), "Booking ID");
            Booking booking = bookingService.findBooking(id)
                    .orElseThrow(() -> new InvalidInputException("No booking found with that ID."));
            if (booking.getUserId() != currentUser.getUserId()) {
                System.out.println("This booking does not belong to you.");
                return;
            }
            if (booking.getStatus() != Booking.Status.CONFIRMED) {
                System.out.println("Only confirmed bookings can be cancelled. Current status: " + booking.getStatus());
                return;
            }
            double refund = bookingService.cancelTicket(booking);
            System.out.printf("Booking cancelled. Refund amount: Rs.%.2f%n", refund);
        } catch (InvalidInputException e) {
            System.out.println(e.getMessage());
        }
    }

    private void viewTicket() {
        try {
            System.out.print("Enter Booking ID: ");
            int id = Validation.parsePositiveInt(sc.nextLine(), "Booking ID");
            Booking booking = bookingService.findBooking(id)
                    .orElseThrow(() -> new InvalidInputException("No booking found with that ID."));
            if (booking.getUserId() != currentUser.getUserId()) {
                System.out.println("This booking does not belong to you.");
                return;
            }
            if (booking.getQrCode() == null) {
                System.out.println("This booking has no digital ticket (status: " + booking.getStatus() + ").");
                return;
            }
            System.out.println(QRGenerator.renderAscii(booking.getQrCode()));
        } catch (InvalidInputException e) {
            System.out.println(e.getMessage());
        }
    }

    private void updateProfile() {
        System.out.print("New city (blank to keep '" + currentUser.getCity() + "'): ");
        String city = sc.nextLine().trim();
        if (!city.isEmpty()) currentUser.setCity(city);

        System.out.print("New preferred category (blank to keep '" + currentUser.getPreferredCategory() + "'): ");
        String category = sc.nextLine().trim();
        if (!category.isEmpty()) currentUser.setPreferredCategory(category);

        userRepository.save(currentUser);
        System.out.println("Profile updated.");
    }

    // ---------------------------------------------------------------
    // ADMIN MENU
    // ---------------------------------------------------------------
    private void adminMenu() {
        System.out.println("\n--- ADMIN MENU (" + currentUser.getName() + ") ---");
        System.out.println("1. Add Event");
        System.out.println("2. Update Event");
        System.out.println("3. Delete Event");
        System.out.println("4. View All Events");
        System.out.println("5. Analytics Dashboard");
        System.out.println("0. Logout");
        System.out.print("Choose: ");
        String choice = sc.nextLine().trim();

        switch (choice) {
            case "1": addEvent(); break;
            case "2": updateEvent(); break;
            case "3": deleteEvent(); break;
            case "4": listEvents(eventRepository.findAll()); break;
            case "5": analyticsService.printDashboard(); break;
            case "0": currentUser = null; break;
            default: System.out.println("Invalid choice.");
        }
    }

    private void addEvent() {
        try {
            System.out.print("Event name: ");
            String name = sc.nextLine().trim();
            Validation.requireNonEmpty(name, "Event name");

            System.out.print("Category: ");
            String category = sc.nextLine().trim();
            Validation.requireNonEmpty(category, "Category");

            System.out.print("Location: ");
            String location = sc.nextLine().trim();
            Validation.requireNonEmpty(location, "Location");

            System.out.print("Date (YYYY-MM-DD): ");
            LocalDate date = Validation.parseFutureOrTodayDate(sc.nextLine(), "Date");

            System.out.print("Base price: ");
            double basePrice = Validation.parsePositiveDouble(sc.nextLine(), "Base price");

            System.out.print("Total seats: ");
            int totalSeats = Validation.parsePositiveInt(sc.nextLine(), "Total seats");

            Event event = new Event(name, category, location, date, basePrice, totalSeats);
            eventRepository.save(event);
            System.out.println("Event created with ID " + event.getEventId());
        } catch (InvalidInputException e) {
            System.out.println("Could not add event: " + e.getMessage());
        }
    }

    private void updateEvent() {
        try {
            System.out.print("Event ID to update: ");
            int id = Validation.parsePositiveInt(sc.nextLine(), "Event ID");
            Event event = eventRepository.findById(id)
                    .orElseThrow(() -> new InvalidInputException("No event found with ID " + id));

            System.out.print("New name (blank to keep): ");
            String name = sc.nextLine().trim();
            if (!name.isEmpty()) event.setName(name);

            System.out.print("New base price (blank to keep): ");
            String priceStr = sc.nextLine().trim();
            if (!priceStr.isEmpty()) event.setBasePrice(Validation.parsePositiveDouble(priceStr, "Base price"));

            eventRepository.save(event);
            System.out.println("Event updated.");
        } catch (InvalidInputException e) {
            System.out.println(e.getMessage());
        }
    }

    private void deleteEvent() {
        try {
            System.out.print("Event ID to delete: ");
            int id = Validation.parsePositiveInt(sc.nextLine(), "Event ID");
            boolean removed = eventRepository.delete(id);
            System.out.println(removed ? "Event deleted." : "No event found with that ID.");
        } catch (InvalidInputException e) {
            System.out.println(e.getMessage());
        }
    }
}
