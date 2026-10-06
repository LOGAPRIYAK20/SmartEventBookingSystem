package model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Represents an event that users can book seats for.
 * Tracks total/available seats so BookingService and PricingService
 * can compute occupancy-based dynamic pricing.
 */
public class Event implements Serializable {
    private static int idCounter = 1;

    private final int eventId;
    private String name;
    private String category;
    private String location;
    private LocalDate date;
    private double basePrice;
    private int totalSeats;
    private int availableSeats;

    public Event(String name, String category, String location, LocalDate date,
                 double basePrice, int totalSeats) {
        this.eventId = idCounter++;
        this.name = name;
        this.category = category;
        this.location = location;
        this.date = date;
        this.basePrice = basePrice;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
    }

    public Event(int eventId, String name, String category, String location, LocalDate date,
                 double basePrice, int totalSeats, int availableSeats) {
        this.eventId = eventId;
        this.name = name;
        this.category = category;
        this.location = location;
        this.date = date;
        this.basePrice = basePrice;
        this.totalSeats = totalSeats;
        this.availableSeats = availableSeats;
        if (eventId >= idCounter) idCounter = eventId + 1;
    }

    public double occupancyPercentage() {
        return ((double) (totalSeats - availableSeats) / totalSeats) * 100.0;
    }

    public boolean reserveSeats(int count) {
        if (count > availableSeats) return false;
        availableSeats -= count;
        return true;
    }

    public void releaseSeats(int count) {
        availableSeats = Math.min(totalSeats, availableSeats + count);
    }

    // ---------- Getters / Setters ----------
    public int getEventId() { return eventId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    public int getTotalSeats() { return totalSeats; }
    public int getAvailableSeats() { return availableSeats; }

    @Override
    public String toString() {
        return String.format("[E%d] %-20s | %-10s | %-10s | %s | Base: Rs.%.2f | Seats: %d/%d (%.1f%% full)",
                eventId, name, category, location, date, basePrice,
                (totalSeats - availableSeats), totalSeats, occupancyPercentage());
    }

    public String toFileLine() {
        return eventId + "|" + name + "|" + category + "|" + location + "|" + date + "|" +
                basePrice + "|" + totalSeats + "|" + availableSeats;
    }
}
