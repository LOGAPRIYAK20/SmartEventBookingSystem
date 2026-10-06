package repository;

import model.Booking;
import util.FileManager;

import java.time.LocalDateTime;
import java.util.*;

public class BookingRepository {

    private static final String FILE_NAME = "bookings.txt";
    private final Map<Integer, Booking> bookingsById = new HashMap<>();

    public BookingRepository() {
        load();
    }

    public Booking save(Booking booking) {
        bookingsById.put(booking.getBookingId(), booking);
        persist();
        return booking;
    }

    public Optional<Booking> findById(int id) {
        return Optional.ofNullable(bookingsById.get(id));
    }

    public List<Booking> findAll() {
        return new ArrayList<>(bookingsById.values());
    }

    public List<Booking> findByUser(int userId) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : bookingsById.values()) {
            if (b.getUserId() == userId) result.add(b);
        }
        return result;
    }

    public List<Booking> findByEvent(int eventId) {
        List<Booking> result = new ArrayList<>();
        for (Booking b : bookingsById.values()) {
            if (b.getEventId() == eventId) result.add(b);
        }
        return result;
    }

    public void persist() {
        List<String> lines = new ArrayList<>();
        for (Booking b : bookingsById.values()) lines.add(b.toFileLine());
        FileManager.writeAll(FILE_NAME, lines);
    }

    private void load() {
        for (String line : FileManager.readAll(FILE_NAME)) {
            String[] p = line.split("\\|", -1);
            if (p.length < 9) continue;
            int id = Integer.parseInt(p[0]);
            int userId = Integer.parseInt(p[1]);
            int eventId = Integer.parseInt(p[2]);
            int seatCount = Integer.parseInt(p[3]);
            String seatLabels = p[4];
            double price = Double.parseDouble(p[5]);
            Booking.Status status = Booking.Status.valueOf(p[6]);
            LocalDateTime time = LocalDateTime.parse(p[7]);
            String qr = p[8];
            bookingsById.put(id, new Booking(id, userId, eventId, seatCount, seatLabels, price, status, time, qr));
        }
    }
}
