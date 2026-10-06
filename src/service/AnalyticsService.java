package service;

import model.Booking;
import model.Event;
import repository.BookingRepository;
import repository.EventRepository;

import java.time.LocalDate;
import java.util.*;

/**
 * Produces admin-only console analytics: revenue, occupancy, most
 * popular event, and cancellation rate — rendered as formatted tables.
 */
public class AnalyticsService {

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public AnalyticsService(BookingRepository bookingRepository, EventRepository eventRepository) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
    }

    public double totalRevenue() {
        double total = 0;
        for (Booking b : bookingRepository.findAll()) {
            if (b.getStatus() == Booking.Status.CONFIRMED) total += b.getPricePaid();
        }
        return total;
    }

    public double revenueOn(LocalDate date) {
        double total = 0;
        for (Booking b : bookingRepository.findAll()) {
            if (b.getStatus() == Booking.Status.CONFIRMED && b.getBookingTime().toLocalDate().equals(date)) {
                total += b.getPricePaid();
            }
        }
        return total;
    }

    public double cancellationRatePercent() {
        List<Booking> all = bookingRepository.findAll();
        if (all.isEmpty()) return 0;
        long cancelled = all.stream().filter(b -> b.getStatus() == Booking.Status.CANCELLED).count();
        return (cancelled * 100.0) / all.size();
    }

    public Optional<Event> mostPopularEvent() {
        Map<Integer, Integer> seatsSold = new HashMap<>();
        for (Booking b : bookingRepository.findAll()) {
            if (b.getStatus() == Booking.Status.CONFIRMED) {
                seatsSold.merge(b.getEventId(), b.getSeatCount(), Integer::sum);
            }
        }
        return seatsSold.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .flatMap(entry -> eventRepository.findById(entry.getKey()));
    }

    public void printDashboard() {
        System.out.println("\n================ ADMIN ANALYTICS DASHBOARD ================");
        System.out.printf("%-30s : Rs.%.2f%n", "Total Revenue", totalRevenue());
        System.out.printf("%-30s : Rs.%.2f%n", "Revenue Today", revenueOn(LocalDate.now()));
        System.out.printf("%-30s : %.2f%%%n", "Cancellation Rate", cancellationRatePercent());
        mostPopularEvent().ifPresentOrElse(
                e -> System.out.printf("%-30s : %s%n", "Most Popular Event", e.getName()),
                () -> System.out.printf("%-30s : %s%n", "Most Popular Event", "N/A")
        );
        System.out.println("-------------------------------------------------------------");
        System.out.printf("%-25s %-10s %-10s %-10s%n", "Event", "Sold", "Total", "Occupancy");
        for (Event e : eventRepository.findAll()) {
            System.out.printf("%-25s %-10d %-10d %.1f%%%n",
                    e.getName(), (e.getTotalSeats() - e.getAvailableSeats()), e.getTotalSeats(), e.occupancyPercentage());
        }
        System.out.println("===============================================================\n");
    }
}
