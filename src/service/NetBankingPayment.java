package service;

import exception.PaymentFailureException;

/**
 * Net Banking payment implementation demonstrating Polymorphism.
 */
public class NetBankingPayment implements PaymentMethod {

    @Override
    public boolean pay(double amount, String credential) throws PaymentFailureException {
        if (credential == null || credential.trim().length() < 3) {
            throw new PaymentFailureException("Invalid Net Banking credentials. Please provide bank username/ID.");
        }
        if (amount <= 0) {
            throw new PaymentFailureException("Payment amount must be positive.");
        }
        return true;
    }

    @Override
    public String methodName() {
        return "Net Banking";
    }
}
