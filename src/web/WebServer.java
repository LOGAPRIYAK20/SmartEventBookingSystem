package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import exception.InvalidInputException;
import exception.PaymentFailureException;
import exception.SeatUnavailableException;
import model.Admin;
import model.Booking;
import model.Event;
import model.User;
import repository.BookingRepository;
import repository.EventRepository;
import repository.UserRepository;
import service.AnalyticsService;
import service.BookingService;
import service.CardPayment;
import service.DynamicPricingStrategy;
import service.PaymentMethod;
import service.PricingStrategy;
import service.RecommendationService;
import service.RefundService;
import service.UpiPayment;
import util.Validation;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Web layer for the Smart Event Booking System.
 *
 * It sits next to the console Menu: same repositories, same services, same
 * data/ files - only the "view" changes. Uses the JDK's built-in HTTP server,
 * so there is nothing to install.
 *
 *   /api/...   JSON REST API
 *   /...       static files from the ./frontend folder
 */
public class WebServer {

    private static final int MAX_SEATS_PER_BOOKING = 10;

    // ---- the same object graph the console Menu builds ----
    private final UserRepository userRepository = new UserRepository();
    private final EventRepository eventRepository = new EventRepository();
    private final BookingRepository bookingRepository = new BookingRepository();
    private final BookingService bookingService =
            new BookingService(bookingRepository, eventRepository, userRepository);
    private final RecommendationService recommendationService =
            new RecommendationService(eventRepository, bookingRepository);
    private final AnalyticsService analyticsService =
            new AnalyticsService(bookingRepository, eventRepository);
    private final PricingStrategy pricingStrategy = new DynamicPricingStrategy();
    private final RefundService refundService = new RefundService();
    private final repository.SentryPassRepository sentryPassRepository = new repository.SentryPassRepository();
    private final service.SentryPassService sentryPassService = new service.SentryPassService(sentryPassRepository);

    /** token -> userId. In-memory, so everyone is logged out when the server restarts. */
    private final Map<String, Integer> sessions = new ConcurrentHashMap<>();

    /** The repositories are plain HashMaps, so API calls are handled one at a time. */
    private final Object lock = new Object();

    private final int port;
    private final Path staticRoot = Paths.get("frontend").toAbsolutePath().normalize();

    public WebServer(int port) {
        this.port = port;
    }

    // ------------------------------------------------------------------
    // Startup
    // ------------------------------------------------------------------
    public void start() throws IOException {
        seedAdminIfMissing();
        seedSampleEventsIfEmpty();

        if (!Files.isDirectory(staticRoot)) {
            System.out.println("WARNING: frontend folder not found at " + staticRoot);
            System.out.println("         Run the server from the project root (the folder that contains 'frontend').");
        }

        // Loopback only: passwords travel over plain HTTP, so keep it on this machine.
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(bookingService::shutdown));

        System.out.println("=====================================================");
        System.out.println(" SMART EVENT BOOKING - WEB");
        System.out.println(" Open  http://localhost:" + port);
        System.out.println(" Admin admin@events.com / admin123");
        System.out.println(" Press Ctrl+C to stop.");
        System.out.println("=====================================================");
    }

    private void seedAdminIfMissing() {
        if (userRepository.findByEmail("admin@events.com").isEmpty()) {
            userRepository.save(new Admin("System Admin", "admin@events.com", "admin123", "HQ", "General"));
            System.out.println("(First run) Default admin created -> admin@events.com / admin123");
        }
    }

    /** So the site isn't empty on first launch. Only runs when there are no events at all. */
    private void seedSampleEventsIfEmpty() {
        if (!eventRepository.findAll().isEmpty()) return;
        LocalDate today = LocalDate.now();
        eventRepository.save(new Event("Indie Music Night", "Music", "Chennai", today.plusDays(12), 800, 120));
        eventRepository.save(new Event("DevConf 2026", "Tech", "Bengaluru", today.plusDays(20), 1500, 300));
        eventRepository.save(new Event("City Marathon", "Sports", "Mumbai", today.plusDays(35), 500, 500));
        eventRepository.save(new Event("Street Food Carnival", "Food", "Hyderabad", today.plusDays(9), 300, 200));
        eventRepository.save(new Event("Zumba Dance Fitness Class", "Fitness", "Chennai", today.plusDays(4), 250, 40));
        eventRepository.save(new Event("Sunrise Yoga & Meditation", "Fitness", "Coimbatore", today.plusDays(6), 200, 60));
        eventRepository.save(new Event("Startup Weekend Hackathon", "Tech", "Chennai", today.plusDays(25), 600, 150));
        eventRepository.save(new Event("Modern Art Exhibition", "Art", "Mumbai", today.plusDays(15), 350, 100));
        eventRepository.save(new Event("Classical Dance Recital", "Dance", "Chennai", today.plusDays(18), 450, 80));
        eventRepository.save(new Event("Kids Science Carnival", "Kids", "Bengaluru", today.plusDays(10), 300, 120));
        eventRepository.save(new Event("Wine & Cheese Tasting Evening", "Food", "Goa", today.plusDays(22), 1200, 50));
        eventRepository.save(new Event("Open Mic Poetry Night", "Comedy", "Pune", today.plusDays(7), 150, 35));
        Event standup = new Event("Standup Comedy Live", "Comedy", "Chennai", today.plusDays(5), 700, 20);
        standup.reserveSeats(17); // nearly full, so surge pricing is visible straight away
        eventRepository.save(standup);
        Event zumba = eventRepository.findAll().stream()
                .filter(e -> e.getName().startsWith("Zumba")).findFirst().orElse(null);
        if (zumba != null) zumba.reserveSeats(33); // also near-full, shows surge on a second category
        System.out.println("(First run) Added sample events across Music, Tech, Sports, Food, Fitness, Art, "
                + "Dance, Kids and Comedy. Delete or edit them from the admin page whenever you like.");
    }

    // ------------------------------------------------------------------
    // API plumbing
    // ------------------------------------------------------------------
    private static final class ApiException extends RuntimeException {
        final int status;
        final String code;

        ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    private static final class ApiResponse {
        final int status;
        final Object body;

        ApiResponse(int status, Object body) {
            this.status = status;
            this.body = body;
        }
    }

    private static ApiResponse ok(Object body) { return new ApiResponse(200, body); }

    private static ApiResponse created(Object body) { return new ApiResponse(201, body); }

    private void handleApi(HttpExchange ex) throws IOException {
        int status;
        Object body;
        try {
            ApiResponse r;
            synchronized (lock) {
                r = route(ex);
            }
            status = r.status;
            body = r.body;
        } catch (ApiException e) {
            status = e.status;
            body = Json.map("error", e.getMessage(), "code", e.code);
        } catch (InvalidInputException e) {
            status = 400;
            body = Json.map("error", e.getMessage(), "code", "INVALID_INPUT");
        } catch (SeatUnavailableException e) {
            status = 409;
            body = Json.map("error", e.getMessage(), "code", "SEATS_UNAVAILABLE");
        } catch (PaymentFailureException e) {
            status = 402;
            body = Json.map("error", e.getMessage(), "code", "PAYMENT_FAILED");
        } catch (IllegalArgumentException e) {
            status = 400;
            body = Json.map("error", "The request could not be read: " + e.getMessage(), "code", "BAD_REQUEST");
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            body = Json.map("error", "Something went wrong on the server.", "code", "SERVER_ERROR");
        }
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private ApiResponse route(HttpExchange ex) throws Exception {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String rest = path.length() > 5 ? path.substring(5) : "";       // strip "/api/"
        String[] parts = rest.isEmpty() ? new String[0] : rest.split("/");

        // Turn /events/3/quote into "GET/events/:id/quote" so routes read like a table.
        StringBuilder key = new StringBuilder(method);
        List<Integer> ids = new ArrayList<>();
        for (String p : parts) {
            if (p.matches("\\d{1,9}")) {
                key.append("/:id");
                ids.add(Integer.parseInt(p));
            } else {
                key.append('/').append(p);
            }
        }

        Map<String, Object> body = new HashMap<>();
        if (method.equals("POST") || method.equals("PUT")) {
            String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!raw.isEmpty()) {
                Object parsed = Json.parse(raw);
                if (!(parsed instanceof Map)) throw new IllegalArgumentException("Expected a JSON object");
                body = (Map<String, Object>) parsed;
            }
        }
        Map<String, String> query = parseQuery(ex.getRequestURI());

        switch (key.toString()) {

            // ---------------- auth ----------------
            case "POST/register": {
                String name = str(body, "name");
                Validation.requireNonEmpty(name, "Name");
                String email = str(body, "email");
                Validation.validateEmail(email);
                if (userRepository.emailExists(email)) {
                    throw new ApiException(409, "EMAIL_TAKEN", "An account with this email already exists.");
                }
                String password = str(body, "password");
                Validation.validatePassword(password);
                String city = str(body, "city");
                Validation.requireNonEmpty(city, "City");
                String category = str(body, "preferredCategory");
                Validation.requireNonEmpty(category, "Preferred category");

                User user = new User(name, email, password, city, category);
                userRepository.save(user);
                String token = UUID.randomUUID().toString();
                sessions.put(token, user.getUserId());
                return created(Json.map("token", token, "user", userJson(user)));
            }

            case "POST/login": {
                String email = str(body, "email");
                String password = str(body, "password");
                User user = email.isEmpty() ? null : userRepository.findByEmail(email).orElse(null);
                if (user == null || !user.checkPassword(password)) {
                    throw new ApiException(401, "BAD_CREDENTIALS", "Invalid email or password.");
                }
                String token = UUID.randomUUID().toString();
                sessions.put(token, user.getUserId());
                return ok(Json.map("token", token, "user", userJson(user)));
            }

            case "POST/logout": {
                String token = bearerToken(ex);
                if (token != null) sessions.remove(token);
                return ok(Json.map("ok", true));
            }

            case "GET/me":
                return ok(userJson(requireUser(ex)));

            case "PUT/me": {
                User user = requireUser(ex);
                String city = str(body, "city");
                if (!city.isEmpty()) user.setCity(city);
                String category = str(body, "preferredCategory");
                if (!category.isEmpty()) user.setPreferredCategory(category);
                userRepository.save(user);
                return ok(userJson(user));
            }

            // ---------------- events (public browsing) ----------------
            case "GET/events": {
                String q = query.getOrDefault("q", "").trim();
                List<Event> list = q.isEmpty() ? eventRepository.findAll() : eventRepository.search(q);
                return ok(eventsJson(upcomingOnly(list), null));
            }

            case "GET/events/recommended": {
                User user = requireUser(ex);
                return ok(eventsJson(upcomingOnly(recommendationService.recommendFor(user)), user));
            }

            case "GET/events/:id":
                return ok(eventJson(findEvent(ids.get(0)), null));

            case "GET/events/:id/quote": {
                Event event = findEvent(ids.get(0));
                int seats = Validation.parsePositiveInt(query.getOrDefault("seats", "1"), "Seat count");
                if (seats > MAX_SEATS_PER_BOOKING) {
                    throw new InvalidInputException("You can book up to " + MAX_SEATS_PER_BOOKING + " seats at a time.");
                }
                boolean waitlist = seats > event.getAvailableSeats();
                Map<String, Object> quote = Json.map("seats", seats, "available", event.getAvailableSeats(),
                        "waitlist", waitlist);
                if (!waitlist) {
                    double total = pricingStrategy.calculatePrice(event, seats);
                    quote.put("total", total);
                    quote.put("perSeat", round2(total / seats));
                    quote.put("basePrice", event.getBasePrice());
                }
                return ok(quote);
            }

            // ---------------- bookings ----------------
            case "POST/bookings": {
                User user = requireUser(ex);
                if (user instanceof Admin) {
                    throw new ApiException(403, "FORBIDDEN", "Admin accounts can't book tickets.");
                }
                int eventId = Validation.parsePositiveInt(str(body, "eventId"), "Event ID");
                int seats = Validation.parsePositiveInt(str(body, "seats"), "Seat count");
                if (seats > MAX_SEATS_PER_BOOKING) {
                    throw new InvalidInputException("You can book up to " + MAX_SEATS_PER_BOOKING + " seats at a time.");
                }
                Event event = findEvent(eventId);
                if (event.getDate().isBefore(LocalDate.now())) {
                    throw new InvalidInputException("This event has already taken place.");
                }
                PaymentMethod method2 = service.PaymentMethodFactory.create(str(body, "method"));
                Booking booking = bookingService.bookTicket(user, event, seats, method2, str(body, "credential"));
                java.util.Map<String, Object> resp = bookingJson(booking);
                if (booking.getStatus() == model.Booking.Status.CONFIRMED) {
                    model.SentryPass pass = sentryPassService.generatePass(booking, user, event);
                    resp.put("sentryPassId", pass.getPassId());
                }
                return created(resp);
            }

            case "GET/bookings": {
                User user = requireUser(ex);
                List<Booking> mine = bookingService.historyForUser(user.getUserId());
                mine.sort(Comparator.comparingInt(Booking::getBookingId).reversed());
                List<Object> out = new ArrayList<>();
                for (Booking b : mine) out.add(bookingJson(b));
                return ok(out);
            }

            case "POST/bookings/:id/cancel": {
                User user = requireUser(ex);
                Booking booking = bookingService.findBooking(ids.get(0))
                        .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "No booking found with that ID."));
                if (booking.getUserId() != user.getUserId()) {
                    throw new ApiException(403, "FORBIDDEN", "This booking does not belong to you.");
                }
                if (booking.getStatus() != Booking.Status.CONFIRMED) {
                    throw new InvalidInputException("Only confirmed bookings can be cancelled. Current status: "
                            + booking.getStatus());
                }
                double refund = bookingService.cancelTicket(booking);
                return ok(Json.map("refund", refund, "booking", bookingJson(booking)));
            }

            // ---------------- admin ----------------
            case "GET/admin/events": {
                requireAdmin(ex);
                return ok(eventsJson(eventRepository.findAll(), null));
            }

            case "POST/admin/events": {
                requireAdmin(ex);
                String name = str(body, "name");
                Validation.requireNonEmpty(name, "Event name");
                String category = str(body, "category");
                Validation.requireNonEmpty(category, "Category");
                String location = str(body, "location");
                Validation.requireNonEmpty(location, "Location");
                LocalDate date = Validation.parseFutureOrTodayDate(str(body, "date"), "Date");
                if (date.isBefore(LocalDate.now())) throw new InvalidInputException("Date can't be in the past.");
                double basePrice = Validation.parsePositiveDouble(str(body, "basePrice"), "Base price");
                int totalSeats = Validation.parsePositiveInt(str(body, "totalSeats"), "Total seats");

                Event event = new Event(name, category, location, date, basePrice, totalSeats);
                eventRepository.save(event);
                return created(eventJson(event, null));
            }

            case "PUT/admin/events/:id": {
                requireAdmin(ex);
                Event event = findEvent(ids.get(0));
                // Validate everything first so a bad field can't leave the event half-updated.
                String name = str(body, "name");
                String category = str(body, "category");
                String location = str(body, "location");
                String dateStr = str(body, "date");
                String priceStr = str(body, "basePrice");
                LocalDate date = dateStr.isEmpty() ? null : Validation.parseFutureOrTodayDate(dateStr, "Date");
                Double price = priceStr.isEmpty() ? null : Validation.parsePositiveDouble(priceStr, "Base price");

                if (!name.isEmpty()) event.setName(name);
                if (!category.isEmpty()) event.setCategory(category);
                if (!location.isEmpty()) event.setLocation(location);
                if (date != null) event.setDate(date);
                if (price != null) event.setBasePrice(price);
                eventRepository.save(event);
                return ok(eventJson(event, null));
            }

            case "DELETE/admin/events/:id": {
                requireAdmin(ex);
                if (!eventRepository.delete(ids.get(0))) {
                    throw new ApiException(404, "NOT_FOUND", "No event found with that ID.");
                }
                return ok(Json.map("ok", true));
            }

            case "GET/admin/analytics": {
                requireAdmin(ex);
                return ok(analyticsJson());
            }

            case "GET/passes": {
                User user = requireUser(ex);
                java.util.List<model.SentryPass> passes = sentryPassService.getUserPasses(user.getUserId());
                java.util.List<Object> out = new ArrayList<>();
                for (model.SentryPass p : passes) {
                    out.add(sentryPassJson(p));
                }
                return ok(out);
            }

            case "POST/admin/checkin": {
                requireAdmin(ex);
                String passId = str(body, "passId");
                try {
                    model.SentryPass pass = sentryPassService.validateAndCheckIn(passId);
                    return ok(sentryPassJson(pass));
                } catch (exception.InvalidSentryPassException | exception.DuplicateCheckInException e) {
                    throw new ApiException(400, "CHECKIN_FAILED", e.getMessage());
                }
            }

            default:
                throw new ApiException(404, "NOT_FOUND", "Unknown API route: " + method + " " + path);
        }
    }

    // ------------------------------------------------------------------
    // Auth helpers
    // ------------------------------------------------------------------
    private String bearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();
        return null;
    }

    private User requireUser(HttpExchange ex) {
        String token = bearerToken(ex);
        Integer userId = token == null ? null : sessions.get(token);
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null) throw new ApiException(401, "UNAUTHENTICATED", "Please sign in to continue.");
        return user;
    }

    private User requireAdmin(HttpExchange ex) {
        User user = requireUser(ex);
        if (!(user instanceof Admin)) {
            throw new ApiException(403, "FORBIDDEN", "This action is for administrators only.");
        }
        return user;
    }

    // ------------------------------------------------------------------
    // JSON mapping
    // ------------------------------------------------------------------
    private Event findEvent(int id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND", "No event found with ID " + id));
    }

    private List<Event> upcomingOnly(List<Event> events) {
        List<Event> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Event e : events) if (!e.getDate().isBefore(today)) out.add(e);
        return out;
    }

    private Map<String, Object> userJson(User u) {
        return Json.map(
                "userId", u.getUserId(),
                "name", u.getName(),
                "email", u.getEmail(),
                "city", u.getCity(),
                "preferredCategory", u.getPreferredCategory(),
                "loyaltyPoints", u.getLoyaltyPoints(),
                "role", u.role());
    }

    /**
     * @param forUser when given (recommendations), adds a short "why this event" list.
     */
    private Map<String, Object> eventJson(Event e, User forUser) {
        boolean soldOut = e.getAvailableSeats() <= 0;
        Map<String, Object> m = Json.map(
                "id", e.getEventId(),
                "name", e.getName(),
                "category", e.getCategory(),
                "location", e.getLocation(),
                "date", e.getDate().toString(),
                "past", e.getDate().isBefore(LocalDate.now()),
                "basePrice", e.getBasePrice(),
                "totalSeats", e.getTotalSeats(),
                "availableSeats", e.getAvailableSeats(),
                "occupancy", Math.round(e.occupancyPercentage() * 10.0) / 10.0,
                "soldOut", soldOut);
        if (!soldOut) {
            double perSeat = pricingStrategy.calculatePrice(e, 1);
            m.put("currentPrice", perSeat);
            m.put("surgeMultiplier", e.getBasePrice() > 0 ? round2(perSeat / e.getBasePrice()) : 1.0);
        } else {
            m.put("currentPrice", null);
            m.put("surgeMultiplier", null);
        }
        if (forUser != null) {
            List<String> reasons = new ArrayList<>();
            if (e.getCategory().equalsIgnoreCase(forUser.getPreferredCategory())) {
                reasons.add("Matches your favourite category");
            }
            if (e.getLocation().equalsIgnoreCase(forUser.getCity())) reasons.add("In your city");
            m.put("reasons", reasons);
        }
        return m;
    }

    private List<Object> eventsJson(List<Event> events, User forUser) {
        List<Event> sorted = new ArrayList<>(events);
        // Recommendations keep their score order; plain lists go by date.
        if (forUser == null) {
            sorted.sort(Comparator.comparing(Event::getDate).thenComparingInt(Event::getEventId));
        }
        List<Object> out = new ArrayList<>();
        for (Event e : sorted) out.add(eventJson(e, forUser));
        return out;
    }

    private Map<String, Object> bookingJson(Booking b) {
        Event event = eventRepository.findById(b.getEventId()).orElse(null);
        String qr = b.getQrCode();
        // BookingRepository stores a missing QR as the text "null"
        if (qr == null || qr.isEmpty() || qr.equals("null")) qr = null;

        Map<String, Object> m = Json.map(
                "id", b.getBookingId(),
                "eventId", b.getEventId(),
                "eventName", event != null ? event.getName() : "(event removed)",
                "eventDate", event != null ? event.getDate().toString() : null,
                "eventLocation", event != null ? event.getLocation() : null,
                "seats", b.getSeatCount(),
                "seatLabels", b.getSeatLabels(),
                "pricePaid", b.getPricePaid(),
                "status", b.getStatus(),
                "bookedAt", b.getBookingTime().toString(),
                "qrCode", qr,
                "waitlisted", b.getStatus() == Booking.Status.WAITLISTED);

        if (b.getStatus() == Booking.Status.CONFIRMED && event != null) {
            m.put("refundIfCancelled", refundService.calculateRefundAmount(event, b.getPricePaid()));
            m.put("refundPercent", (int) Math.round(refundService.refundPercentage(event) * 100));
        }
        if (b.getStatus() == Booking.Status.WAITLISTED) {
            int pos = bookingService.waitlistPosition(b.getEventId(), b.getBookingId());
            m.put("waitlistPosition", pos > 0 ? pos : null);
        }
        return m;
    }

    private Map<String, Object> sentryPassJson(model.SentryPass p) {
        return Json.map(
                "passId", p.getPassId(),
                "bookingId", p.getBookingId(),
                "userId", p.getUserId(),
                "userName", p.getUserName(),
                "eventId", p.getEventId(),
                "eventName", p.getEventName(),
                "eventDate", p.getEventDate(),
                "eventTime", p.getEventTime(),
                "venue", p.getVenue(),
                "seatNumbers", p.getSeatNumbers(),
                "amountPaid", p.getAmountPaid(),
                "issuedAt", p.getIssuedAt() != null ? p.getIssuedAt().toString() : null,
                "validUntil", p.getValidUntil() != null ? p.getValidUntil().toString() : null,
                "passStatus", p.getPassStatus().toString(),
                "qrCode", p.getQrCode(),
                "checkedIn", p.isCheckedIn(),
                "checkInTime", p.getCheckInTime() != null ? p.getCheckInTime().toString() : null
        );
    }

    private Map<String, Object> analyticsJson() {
        List<Object> rows = new ArrayList<>();
        List<Event> events = new ArrayList<>(eventRepository.findAll());
        events.sort(Comparator.comparing(Event::getDate).thenComparingInt(Event::getEventId));
        for (Event e : events) {
            double revenue = 0;
            for (Booking b : bookingRepository.findByEvent(e.getEventId())) {
                if (b.getStatus() == Booking.Status.CONFIRMED) revenue += b.getPricePaid();
            }
            rows.add(Json.map(
                    "id", e.getEventId(),
                    "name", e.getName(),
                    "sold", e.getTotalSeats() - e.getAvailableSeats(),
                    "total", e.getTotalSeats(),
                    "occupancy", Math.round(e.occupancyPercentage() * 10.0) / 10.0,
                    "revenue", round2(revenue)));
        }

        int confirmed = 0, cancelled = 0, waitlisted = 0;
        for (Booking b : bookingRepository.findAll()) {
            if (b.getStatus() == Booking.Status.CONFIRMED) confirmed++;
            else if (b.getStatus() == Booking.Status.CANCELLED) cancelled++;
            else waitlisted++;
        }
        int customers = 0;
        for (User u : userRepository.findAll()) if (!(u instanceof Admin)) customers++;


        Event popular = analyticsService.mostPopularEvent().orElse(null);
        long activePasses = sentryPassService.getAllPasses().stream().filter(p -> p.getPassStatus() == model.SentryPass.PassStatus.ACTIVE).count();
        long checkedIn = sentryPassService.getAllPasses().stream().filter(p -> p.isCheckedIn()).count();
        return Json.map(
                "totalRevenue", round2(analyticsService.totalRevenue()),
                "revenueToday", round2(analyticsService.revenueOn(LocalDate.now())),
                "cancellationRate", round2(analyticsService.cancellationRatePercent()),
                "mostPopularEvent", popular != null ? popular.getName() : null,
                "confirmedBookings", confirmed,
                "cancelledBookings", cancelled,
                "waitlistedBookings", waitlisted,
                "customers", customers,
                "activeSentryPasses", activePasses,
                "checkedInUsers", checkedIn,
                "events", rows);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Reads a JSON field as trimmed text; whole numbers lose their ".0". */
    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return "";
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return v.toString().trim();
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> out = new HashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Static files (the frontend)
    // ------------------------------------------------------------------
    private static final Map<String, String> CONTENT_TYPES = new LinkedHashMap<>();

    static {
        CONTENT_TYPES.put(".html", "text/html; charset=utf-8");
        CONTENT_TYPES.put(".css", "text/css; charset=utf-8");
        CONTENT_TYPES.put(".js", "text/javascript; charset=utf-8");
        CONTENT_TYPES.put(".json", "application/json; charset=utf-8");
        CONTENT_TYPES.put(".svg", "image/svg+xml");
        CONTENT_TYPES.put(".png", "image/png");
        CONTENT_TYPES.put(".jpg", "image/jpeg");
        CONTENT_TYPES.put(".ico", "image/x-icon");
        CONTENT_TYPES.put(".woff2", "font/woff2");
    }

    private void handleStatic(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file = staticRoot.resolve(path.substring(1)).normalize();
        // normalize() + startsWith() stops "../" from escaping the frontend folder
        if (!file.startsWith(staticRoot) || !Files.isRegularFile(file)) {
            byte[] msg = "Not found".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
            return;
        }

        String name = file.getFileName().toString().toLowerCase();
        String type = "application/octet-stream";
        for (Map.Entry<String, String> e : CONTENT_TYPES.entrySet()) {
            if (name.endsWith(e.getKey())) {
                type = e.getValue();
                break;
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if (method.equals("HEAD")) {
            ex.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            ex.sendResponseHeaders(200, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
