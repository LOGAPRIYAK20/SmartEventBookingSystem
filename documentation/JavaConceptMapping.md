# Java Concept Mapping & Viva Guide

This document maps all core Java and Object-Oriented Programming (OOP) concepts to their exact implementations in the **Smart Event Ticket Booking and Management System with Digital Sentry Pass**.

---

## 1. Object-Oriented Programming (OOP) Principles

### 1.1 Encapsulation
- **What it is**: Hiding internal object state by declaring fields `private` and exposing controlled access via getters, setters, and domain methods.
- **Implemented in**:
  - `model.User`: `userId`, `name`, `email`, `password`, `loyaltyPoints` are private. Access is mediated by `checkPassword()`, `addLoyaltyPoints()`, `redeemLoyaltyPoints()`.
  - `model.Event`: `totalSeats`, `availableSeats`, `basePrice` are private. Mutated only via `reserveSeats(count)` and `releaseSeats(count)`.
  - `model.Booking`: Private fields for `bookingId`, `userId`, `seatLabels`, `pricePaid`, `status`.
  - `model.SentryPass`: Encapsulates all pass credentials and state (`passId`, `validUntil`, `passStatus`, `checkedIn`, `checkInTime`).

### 1.2 Inheritance
- **What it is**: Reusing and specializing behaviors of a parent class in a subclass.
- **Implemented in**:
  - `model.Admin extends model.User`: `Admin` inherits user identity, credentials, and profile attributes, but overrides `role()` to return `"ADMIN"`, granting administrator privileges across controllers.

### 1.3 Abstraction
- **What it is**: Defining contracts and common behaviors without exposing implementation details.
- **Implemented in**:
  - `service.PaymentMethod` (abstract class): Defines `abstract String methodName()` and `abstract void pay(double amount, String credential) throws PaymentFailureException`.
  - `service.PricingStrategy` (interface): Defines `double calculatePrice(Event event, int seatsRequested)`.

### 1.4 Polymorphism
- **What it is**: Treating different subclasses/implementations uniformly through their common superclass or interface reference.
- **Implemented in**:
  - **Payment Polymorphism**: A reference of type `PaymentMethod` can hold `UpiPayment`, `CardPayment`, or `NetBankingPayment`.
  - **Pricing Polymorphism**: A reference of type `PricingStrategy` is instantiated with `DynamicPricingStrategy`, calculating prices dynamically based on event occupancy.
  - **Method Overriding (`@Override`)**: `Admin.role()`, `UpiPayment.pay()`, `CardPayment.pay()`, `NetBankingPayment.pay()`.

---

## 2. Design Patterns

### 2.1 Strategy Pattern
- **Problem**: Ticket pricing rules vary dynamically based on availability without changing the booking workflow.
- **Solution**: `PricingStrategy` interface implemented by `DynamicPricingStrategy`. Decoupled from `BookingService`.

### 2.2 Factory Pattern
- **Problem**: Creating the appropriate payment processor cleanly based on user input.
- **Solution**: `service.PaymentMethodFactory.create(String type)` produces `UpiPayment`, `CardPayment`, or `NetBankingPayment` using modern Java 21 switch expressions.

### 2.3 Observer / Audit Pattern
- **Problem**: Notifying subsystems and logging events whenever a booking or check-in occurs.
- **Solution**: Decoupled calls to `FileManager.log()` and `FileManager.backup()` for each lifecycle transition (`BOOKING_CONFIRMED`, `SENTRY_PASS_GENERATED`, `ENTRY_APPROVED`, etc.).

---

## 3. Java 21 & Modern Language Features

### 3.1 Switch Expressions
- **Used in**: `PaymentMethodFactory`
```java
return switch (type.trim().toUpperCase()) {
    case "CARD" -> new CardPayment();
    case "NETBANKING" -> new NetBankingPayment();
    case "UPI" -> new UpiPayment();
    default -> new UpiPayment();
};
```

### 3.2 Java Streams & Lambda Expressions
- **Filtering & Matching**:
  - `SentryPassRepository.findById(String passId)` uses `passes.stream().filter(...).findFirst()`.
  - `SentryPassRepository.findByUserId(int userId)` uses `passes.stream().filter(...).collect(Collectors.toList())`.
- **Aggregation & Analytics**:
  - `WebServer.analyticsJson()` computes live counts using `stream().filter(p -> p.isCheckedIn()).count()`.
  - `AnalyticsService` computes revenue, occupancy, and popular events via Stream pipelines.

### 3.3 Optional Handling
- Eliminates `NullPointerException` bugs across all repository lookups:
  - `eventRepository.findById(int id)` returns `Optional<Event>`.
  - `sentryPassRepository.findById(String id)` returns `Optional<SentryPass>`.
  - `bookingRepository.findById(int id)` returns `Optional<Booking>`.

---

## 4. Concurrency & Multithreading

- **Seat Lock Manager (`service.SeatLockManager`)**:
  - Uses `ScheduledExecutorService` to lock seats temporarily for 120 seconds.
  - Automatically releases locked seats back to the event pool if the user does not finish payment within the timeout window.
  - Thread-safe tracking with `ConcurrentHashMap`.

---

## 5. Collections Framework

| Collection | Class | Purpose |
|------------|-------|---------|
| `ArrayList<T>` | `EventRepository`, `SentryPassRepository` | Dynamic in-memory listing of events and passes |
| `HashMap<K, V>` | `UserRepository`, `WebServer` | O(1) lookups by ID and active user sessions |
| `ConcurrentHashMap<K, V>` | `WebServer`, `SeatLockManager` | Thread-safe session tracking and seat locking |
| `Queue<T>` / `LinkedList` | `WaitlistService` | FIFO queue for auto-confirming waitlisted users |
| `Set<String>` | `BookingService`, Frontend | Unique seat labels (`A1, A2, B3`) |

---

## 6. Exception Handling

Custom application-specific exceptions derived from `Exception`:
- `SeatUnavailableException`: Thrown if a requested seat is already locked or booked.
- `PaymentFailureException`: Thrown if simulated payment credentials are invalid.
- `InvalidSentryPassException`: Thrown if a pass is not found, expired, or cancelled.
- `DuplicateCheckInException`: Thrown if an attendee attempts to scan an already-used pass twice.
- All exceptions are captured by `WebServer` and converted to friendly HTTP JSON responses without exposing technical stack traces to users.

---

## 7. File I/O & Persistence

- Zero external database setup needed!
- Uses `BufferedReader`, `BufferedWriter`, and `try-with-resources` inside `util.FileManager` and repository classes.
- Persistent files saved under `data/`:
  - `users.txt`
  - `events.txt`
  - `bookings.txt`
  - `passes.txt`
  - `logs.txt`
  - `backup.txt`
