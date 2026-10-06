package service;

/**
 * Loyalty & Reward Points:
 *  Earn 1 point per Rs.100 spent (rounded down).
 *  100 points can be redeemed for a flat Rs.200 discount.
 */
public class LoyaltyService {

    private static final double RS_PER_POINT = 100.0;
    public static final int REDEEM_THRESHOLD = 100;
    public static final double REDEEM_DISCOUNT = 200.0;

    public int pointsEarned(double amountPaid) {
        return (int) (amountPaid / RS_PER_POINT);
    }

    public boolean canRedeem(int currentPoints) {
        return currentPoints >= REDEEM_THRESHOLD;
    }
}
