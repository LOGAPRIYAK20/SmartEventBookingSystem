# 🎟️ Smart Event Booking System
### with Digital Sentry Pass

A complete Smart Event Ticket Booking and Management System built with Java 21 and a modern browser frontend. No external database required — runs out of the box.

---

## 🚀 How to Run

### Quick Start (Windows)
Double-click **`run.bat`** in the project folder.

### Manual (PowerShell / Command Prompt)
```cmd
cd SmartEventBookingSystem
mkdir out
javac -d out -encoding UTF-8 src\exception\*.java src\model\*.java src\repository\*.java src\util\*.java src\service\*.java src\web\*.java src\WebMain.java
java -cp out WebMain
```

Then open: **http://localhost:8080**

> Requires Java 21 (JDK). Download from https://adoptium.net

---

## 🔑 Demo Credentials

| Role  | Email               | Password  |
|-------|---------------------|-----------|
| Admin | admin@events.com    | admin123  |
| User  | Register a new account at `/register` |

---

## ✨ Features

### User Features
- 🔐 Register / Login / Profile
- 🎟️ Browse & search events (by name, city, category, date)
- 💺 Visual seat map with smart recommendations
- 💰 Dynamic pricing (price increases as seats fill up)
- 💳 Multiple payment methods: UPI, Card, Net Banking
- ✅ Booking confirmation with Sentry Pass generation
- 🎫 Digital QR-based Sentry Pass
- 📋 My Bookings with cancellation & refund info
- 🎫 My Sentry Passes with print support
- ⭐ Loyalty points earned on every booking
- 🕐 Waitlist when event is sold out

### Admin Features
- 📊 Dashboard with live stats
- ➕ Add / Edit / Delete events
- 📈 Analytics with occupancy and revenue
- ✅ Sentry Check-In — scan pass ID to approve entry
- 🚫 Duplicate entry prevention

---

## 🧠 Java Concepts Demonstrated

| Concept | Where Used |
|---------|-----------|
| **Encapsulation** | User, Event, Booking, SentryPass — private fields + getters/setters |
| **Inheritance** | Admin extends User |
| **Abstraction** | PaymentMethod (abstract), PricingStrategy (interface) |
| **Polymorphism** | UpiPayment, CardPayment, NetBankingPayment via PaymentMethod |
| **Enums** | Booking.Status, SentryPass.PassStatus |
| **Collections** | ArrayList, HashMap, Queue, Set throughout |
| **Streams + Lambda** | Analytics, recommendations, filtering events |
| **Optional** | Repository lookup methods |
| **Multithreading** | SeatLockManager — temporary seat locking with auto-release |
| **Custom Exceptions** | SeatUnavailableException, PaymentFailureException, InvalidSentryPassException, DuplicateCheckInException |
| **File Handling** | All data persisted to data/*.txt via BufferedReader/Writer |
| **Strategy Pattern** | PricingStrategy → DynamicPricingStrategy |
| **Factory Pattern** | PaymentMethodFactory |
| **Method Overloading** | SentryPassService.generatePass() |
| **Records/Switch** | Java 21 switch expressions in PaymentMethodFactory |

---

## 📁 Project Structure

```
SmartEventBookingSystem/
├── src/
│   ├── model/          User, Admin, Event, Booking, Payment, SentryPass
│   ├── repository/     UserRepo, EventRepo, BookingRepo, SentryPassRepo
│   ├── service/        BookingService, SentryPassService, PaymentMethods...
│   ├── exception/      Custom exceptions
│   ├── util/           FileManager, QRGenerator, Validation
│   └── web/            WebServer (REST API), Json
├── frontend/
│   └── index.html      Complete SPA (HTML + CSS + JS)
├── data/               Persistent storage (auto-created)
├── out/                Compiled classes
├── run.bat             One-click start (Windows)
└── README.md
```

---

## 🎯 Demo Flow (2-min presentation)

1. **Login** as admin → check Dashboard
2. **Browse Events** → pick "Tech Fest"
3. **Click "Book Now"** → seat map appears
4. **Click "Smart Suggest"** → seats auto-selected
5. **Pay** with UPI → Booking Confirmed!
6. **Click "View Sentry Pass"** → see digital QR pass
7. **Admin → Sentry Check-In** → enter the Pass ID
8. ✅ **ENTRY APPROVED** appears
9. Try the same pass again → ❌ **ENTRY DENIED** (already used)

---

## 🔄 Complete User Flow

```
Register/Login → Browse Events → Select Event →
Smart Seat Suggestion → Select Seats → Dynamic Pricing →
Checkout → Payment → Booking Confirmed →
Sentry Pass Generated → View QR Pass →
Admin Check-In → ENTRY APPROVED → Pass = USED
```

---

## 📦 Storage Files (data/)

| File         | Contents                    |
|--------------|-----------------------------|
| users.txt    | Registered users            |
| events.txt   | Events                      |
| bookings.txt | All bookings                |
| passes.txt   | Sentry passes               |
| logs.txt     | Audit log                   |
| backup.txt   | Backup of confirmed bookings|

All data survives server restart.

---

## 🔧 Technology

- **Backend**: Java 21, JDK HttpServer (no framework)
- **Frontend**: HTML5, CSS3, Vanilla JavaScript (no build step)
- **Storage**: Plain text files (no database required)
- **Port**: 8080 (localhost only)
