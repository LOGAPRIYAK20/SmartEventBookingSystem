package service;

import model.Event;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Smart Refund System:
 *  >7 days before event  -> 100% refund
 *  3-7 days before event -> 70% refund
 *  1-3 days before event -> 40% refund
 *  <24 hrs before event  -> 0% refund
 */
public class RefundService {

    public double refundPercentage(Event event) {
        long daysUntilEvent = ChronoUnit.DAYS.between(LocalDate.now(), event.getDate());

        if (daysUntilEvent > 7) return 1.00;
        if (daysUntilEvent >= 3) return 0.70;
        if (daysUntilEvent >= 1) return 0.40;
        return 0.00;
    }

    public double calculateRefundAmount(Event event, double amountPaid) {
        double pct = refundPercentage(event);
        return Math.round(amountPaid * pct * 100.0) / 100.0;
    }
}
