# Viva & Project Defense Guide: Smart Event Ticket Booking & Sentry Pass System

This guide prepares you for common evaluation questions during college project reviews and viva examinations.

---

### Q1: What is the high-level architecture of this application?
**Answer:**
The application follows a clean, modular multi-tier architecture:
- **Presentation Layer (Frontend)**: Single Page Application (SPA) built with HTML5, CSS3, and modern Vanilla JavaScript, making asynchronous REST API calls via `fetch()`.
- **Controller / Web Layer (`web/`)**: Uses Java's lightweight built-in `com.sun.net.httpserver.HttpServer` (`WebServer.java`) to serve JSON REST endpoints and static files without requiring third-party frameworks like Spring or Tomcat.
- **Service Layer (`service/`)**: Contains pure business logic (dynamic pricing, seat recommendation algorithms, seat locking, waitlist queues, payment processing, and Sentry pass check-in).
- **Repository Layer (`repository/`)**: Abstracts data persistence and collection manipulation, maintaining in-memory domain models and keeping them synchronized with flat files.
- **Data Layer (`data/`)**: Human-readable, pipe-delimited text files (`.txt`) providing zero-dependency persistence that survives application restarts.

---

### Q2: How is the Strategy Pattern implemented?
**Answer:**
The Strategy pattern is used for **Dynamic Pricing**:
- `PricingStrategy` is an interface with the method:
  ```java
  double calculatePrice(Event event, int seatsRequested);
  ```
- `DynamicPricingStrategy` implements this interface and adjusts the price dynamically based on event occupancy (e.g., base price for low occupancy, 1.2x surge multiplier when occupancy exceeds 70%, 1.4x surge when seats are below 15%).
- `BookingService` depends only on the abstraction `PricingStrategy`, allowing pricing algorithms to be swapped or extended without modifying the booking workflow.

---

### Q3: How is the Factory Pattern implemented?
**Answer:**
The Factory pattern is implemented in `PaymentMethodFactory`:
```java
public class PaymentMethodFactory {
    public static PaymentMethod create(String type) {
        return switch (type.trim().toUpperCase()) {
            case "CARD" -> new CardPayment();
            case "NETBANKING" -> new NetBankingPayment();
            case "UPI" -> new UpiPayment();
            default -> new UpiPayment();
        };
    }
}
```
It encapsulates object instantiation for the polymorphic `PaymentMethod` hierarchy and uses modern Java 21 **switch expressions**.

---

### Q4: How is seat locking and multithreading handled to prevent race conditions?
**Answer:**
`SeatLockManager` uses:
1. `ConcurrentHashMap<String, Long>` to safely store active seat locks across multiple HTTP worker threads without data corruption.
2. A `ScheduledExecutorService` running in the background. When a user begins checkout, their seats are locked for **120 seconds**. If payment is not completed before the timer expires, the background thread automatically releases the seats back to the available seat pool.
3. If payment succeeds, `confirmLock()` permanently updates the seat count and cleans up the lock record.

---

### Q5: What is the Sentry Pass and how does it prevent duplicate entry?
**Answer:**
- When a booking reaches the `CONFIRMED` status, `SentryPassService.generatePass()` automatically creates a digital pass with:
  - A unique, readable Pass ID format: `SP-YYYY-<HEX>` (e.g., `SP-2026-0D5C43`).
  - A deterministic QR code containing pass ID, booking ID, and event ID.
  - Initial `PassStatus.ACTIVE`.
- When an administrator scans or submits the Pass ID at `/admin/checkin`:
  1. The system checks if the pass exists (throws `InvalidSentryPassException` if not found).
  2. The system checks if `checkedIn == true` or status is `USED` (throws `DuplicateCheckInException` if already scanned).
  3. If valid, it records the exact `checkInTime` and transitions the status to `USED`.
  4. Subsequent scan attempts are immediately denied with an alert stating the pass has already been used.

---

### Q6: How are Java 21 and modern Java features utilized?
**Answer:**
- **Switch Expressions**: Used in `PaymentMethodFactory` for clean, exhaustive pattern-matching syntax.
- **Java Streams & Lambdas**: Used extensively for filtering events (`events.stream().filter(...).collect(...)`), finding popular events, calculating total revenue, and occupancy percentages in `AnalyticsService`.
- **Optional API**: `Optional<User>`, `Optional<Event>`, and `Optional<SentryPass>` used in repositories to avoid `NullPointerException`.
- **String Templates / Modern Collections**: `Map.of`, immutable lists, and formatted string operations.

---

### Q7: Why use file persistence instead of an external database like MySQL?
**Answer:**
- **Zero Configuration & Portability**: Runs out-of-the-box on any machine with Java without installing database servers or configuring drivers/credentials.
- **Educational Value**: Demonstrates foundational Java I/O concepts using `BufferedReader`, `BufferedWriter`, and `try-with-resources` (`util.FileManager`).
- **Audit Logging**: Every operation (login, booking, pass generation, check-in, cancellation) is written to append-only logs (`data/logs.txt` and `data/backup.txt`).

---

### Q8: What custom exceptions exist and why?
**Answer:**
Custom exceptions inherit from `java.lang.Exception`:
- `SeatUnavailableException`: When a requested seat is already taken or locked.
- `PaymentFailureException`: When simulated credentials fail validation.
- `InvalidSentryPassException`: When a pass does not exist, is expired, or is cancelled.
- `DuplicateCheckInException`: When an already-checked-in pass is presented again.
These allow the web layer (`WebServer.java`) to translate business logic failures into specific HTTP status codes (e.g., 400 Bad Request, 402 Payment Required, 409 Conflict) without exposing raw stack traces.
