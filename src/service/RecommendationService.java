package service;

import model.Booking;
import model.Event;
import model.User;
import repository.BookingRepository;
import repository.EventRepository;

import java.util.*;

/**
 * Scores upcoming events for a given user based on:
 *  +3 points if category matches user's preferred category
 *  +2 points if location matches user's city
 *  +1 point for every past booking the user made in that category (history-based)
 * Higher score = shown first. Demonstrates Collections + HashMap + simple data processing.
 */
public class RecommendationService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public RecommendationService(EventRepository eventRepository, BookingRepository bookingRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<Event> recommendFor(User user) {
        Map<String, Integer> categoryHistoryCount = new HashMap<>();
        for (Booking b : bookingRepository.findByUser(user.getUserId())) {
            eventRepository.findById(b.getEventId()).ifPresent(ev ->
                    categoryHistoryCount.merge(ev.getCategory(), 1, Integer::sum));
        }

        Map<Event, Integer> scored = new LinkedHashMap<>();
        for (Event event : eventRepository.findAll()) {
            if (event.getAvailableSeats() <= 0) continue; // skip sold-out events
            int score = 0;
            if (event.getCategory().equalsIgnoreCase(user.getPreferredCategory())) score += 3;
            if (event.getLocation().equalsIgnoreCase(user.getCity())) score += 2;
            score += categoryHistoryCount.getOrDefault(event.getCategory(), 0);
            scored.put(event, score);
        }

        List<Event> sorted = new ArrayList<>(scored.keySet());
        sorted.sort((a, b) -> scored.get(b) - scored.get(a));
        return sorted;
    }
}
