package service;

import model.Event;

/**
 * Occupancy-based dynamic pricing:
 *  0%   - 79% full -> base price               (1.00x)
 *  80%  - 89% full -> +20% surge                (1.20x)
 *  90%  - 94% full -> +40% surge                (1.40x)
 *  95%  - 99% full -> +70% surge (last-10 zone)  (1.70x)
 *  100% full        -> sold out (throws upstream in BookingService)
 *
 * This matches the brief: "80% left = base price, last 10 seats = premium price"
 * by keying the surge off occupancy percentage rather than a fixed count,
 * so it scales correctly for any venue size.
 */
public class DynamicPricingStrategy implements PricingStrategy {

    @Override
    public double calculatePrice(Event event, int seatsRequested) {
        double occupancyAfterBooking =
                ((double) (event.getTotalSeats() - event.getAvailableSeats() + seatsRequested)
                        / event.getTotalSeats()) * 100.0;

        double multiplier;
        if (occupancyAfterBooking >= 95) {
            multiplier = 1.70;
        } else if (occupancyAfterBooking >= 90) {
            multiplier = 1.40;
        } else if (occupancyAfterBooking >= 80) {
            multiplier = 1.20;
        } else {
            multiplier = 1.00;
        }

        double pricePerSeat = event.getBasePrice() * multiplier;
        return round2(pricePerSeat * seatsRequested);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
