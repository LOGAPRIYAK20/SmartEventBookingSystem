package service;

import model.Booking;
import model.Event;
import repository.BookingRepository;
import repository.EventRepository;
import util.FileManager;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a FIFO waiting list per event using Queue/LinkedList.
 * When a seat frees up (cancellation), the next waitlisted booking
 * is automatically confirmed - demonstrates Queue + Exception handling.
 */
public class WaitlistService {

    // eventId -> queue of waitlisted booking IDs, in the order they joined
    private final Map<Integer, Queue<Integer>> waitlists = new ConcurrentHashMap<>();

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public WaitlistService(BookingRepository bookingRepository, EventRepository eventRepository) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
    }

    public void addToWaitlist(int eventId, int bookingId) {
        waitlists.computeIfAbsent(eventId, k -> new LinkedList<>()).add(bookingId);
        FileManager.log("Booking #" + bookingId + " added to waitlist for event " + eventId);
    }

    public int waitlistPosition(int eventId, int bookingId) {
        Queue<Integer> queue = waitlists.get(eventId);
        if (queue == null) return -1;
        int pos = 1;
        for (int id : queue) {
            if (id == bookingId) return pos;
            pos++;
        }
        return -1;
    }

    /**
     * Called whenever seats become available for an event (e.g. after a cancellation).
     * Auto-confirms waitlisted bookings while seats remain.
     */
    public synchronized void tryAutoConfirm(int eventId) {
        Queue<Integer> queue = waitlists.get(eventId);
        if (queue == null || queue.isEmpty()) return;

        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return;

        while (!queue.isEmpty() && event.getAvailableSeats() > 0) {
            int bookingId = queue.peek();
            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking == null || booking.getStatus() != Booking.Status.WAITLISTED) {
                queue.poll();
                continue;
            }
            if (booking.getSeatCount() > event.getAvailableSeats()) {
                break; // not enough seats yet for this person - wait for more cancellations
            }
            event.reserveSeats(booking.getSeatCount());
            booking.setStatus(Booking.Status.CONFIRMED);
            bookingRepository.save(booking);
            eventRepository.save(event);
            queue.poll();
            FileManager.log("Auto-confirmed waitlisted booking #" + bookingId + " for event " + eventId);
            FileManager.backup("WAITLIST_AUTO_CONFIRM booking=" + bookingId + " event=" + eventId);
        }
    }
}
