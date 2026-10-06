package service;

import exception.PaymentFailureException;

/** Simulated UPI payment: credential = a valid-looking UPI ID (name@bank). */
public class UpiPayment implements PaymentMethod {

    @Override
    public boolean pay(double amount, String credential) throws PaymentFailureException {
        if (!credential.matches("^[\\w.\\-]{2,}@[a-zA-Z]{2,}$")) {
            throw new PaymentFailureException("Invalid UPI ID format (expected name@bank).");
        }
        if (amount <= 0) {
            throw new PaymentFailureException("Payment amount must be positive.");
        }
        return true;
    }

    @Override
    public String methodName() {
        return "UPI";
    }
}
