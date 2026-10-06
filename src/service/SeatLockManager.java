package service;

import repository.EventRepository;
import util.FileManager;
import model.Event;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Temporarily reserves seats while a user completes payment.
 * Uses a background daemon thread pool (multithreading) so the lock
 * auto-expires and releases seats if payment isn't completed in time -
 * this prevents double booking without blocking the console UI.
 */
public class SeatLockManager {

    private static final int LOCK_SECONDS = 120; // 2 minutes, as per the project brief

    private final EventRepository eventRepository;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "seat-lock-worker");
                t.setDaemon(true);
                return t;
            });

    // lockKey ("eventId:userId") -> scheduled release task, so it can be cancelled if payment succeeds
    private final Map<String, ScheduledFuture<?>> activeLocks = new ConcurrentHashMap<>();

    public SeatLockManager(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /** Locks (reserves) seats immediately and schedules an auto-release. */
    public boolean lockSeats(int eventId, int userId, int seatCount) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null || !event.reserveSeats(seatCount)) {
            return false;
        }
        eventRepository.save(event);

        String key = eventId + ":" + userId;
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            Event current = eventRepository.findById(eventId).orElse(null);
            if (current != null) {
                current.releaseSeats(seatCount);
                eventRepository.save(current);
                FileManager.log("Seat lock expired -> released " + seatCount +
                        " seat(s) for event " + eventId + " (user " + userId + ")");
            }
            activeLocks.remove(key);
        }, LOCK_SECONDS, TimeUnit.SECONDS);

        activeLocks.put(key, task);
        FileManager.log("Locked " + seatCount + " seat(s) for event " + eventId +
                " (user " + userId + ") for " + LOCK_SECONDS + "s");
        return true;
    }

    /** Call this once payment succeeds so the auto-release timer is cancelled. */
    public void confirmLock(int eventId, int userId) {
        String key = eventId + ":" + userId;
        ScheduledFuture<?> task = activeLocks.remove(key);
        if (task != null) task.cancel(false);
    }

    /** Call this if the user backs out before paying, to release seats immediately. */
    public void releaseLock(int eventId, int userId, int seatCount) {
        String key = eventId + ":" + userId;
        ScheduledFuture<?> task = activeLocks.remove(key);
        if (task != null) task.cancel(false);
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event != null) {
            event.releaseSeats(seatCount);
            eventRepository.save(event);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
