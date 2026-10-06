package service;

import model.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests seat labels for a booking:
 *  - Groups (2+ seats) get adjacent seats in the same row where possible.
 *  - Solo users get a seat close to the row's centre (best view).
 *
 * Seats are modelled virtually as rows of 10 (A1..A10, B1..B10, ...)
 * sized to the event's total capacity - no separate seat map file needed.
 */
public class SeatRecommendationService {

    private static final int SEATS_PER_ROW = 10;

    public List<String> recommendSeats(Event event, int seatsRequested) {
        int bookedSoFar = event.getTotalSeats() - event.getAvailableSeats();
        List<String> suggestion = new ArrayList<>();

        if (seatsRequested > 1) {
            // Try to find `seatsRequested` adjacent seats starting from the next free block
            int startIndex = bookedSoFar; // naive: assume seats fill in order for the demo model
            for (int i = 0; i < seatsRequested; i++) {
                suggestion.add(seatLabel(startIndex + i));
            }
        } else {
            // Solo -> aim for the middle of the current row for the best view
            int row = bookedSoFar / SEATS_PER_ROW;
            int centreOffset = SEATS_PER_ROW / 2;
            int centreIndex = row * SEATS_PER_ROW + centreOffset;
            if (centreIndex < event.getTotalSeats() && centreIndex >= bookedSoFar) {
                suggestion.add(seatLabel(centreIndex));
            } else {
                suggestion.add(seatLabel(bookedSoFar));
            }
        }
        return suggestion;
    }

    private String seatLabel(int index) {
        int row = index / SEATS_PER_ROW;
        int col = (index % SEATS_PER_ROW) + 1;
        char rowChar = (char) ('A' + row);
        return "" + rowChar + col;
    }
}
