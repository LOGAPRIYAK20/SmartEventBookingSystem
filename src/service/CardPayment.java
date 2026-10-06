package service;

import exception.PaymentFailureException;

/** Simulated card payment: credential = 16-digit card number. */
public class CardPayment implements PaymentMethod {

    @Override
    public boolean pay(double amount, String credential) throws PaymentFailureException {
        String digits = credential.replaceAll("\\s", "");
        if (!digits.matches("\\d{16}")) {
            throw new PaymentFailureException("Card number must be exactly 16 digits.");
        }
        if (amount <= 0) {
            throw new PaymentFailureException("Payment amount must be positive.");
        }
        // Simulated gateway: cards ending in "0000" are treated as declined, for demo/testing.
        if (digits.endsWith("0000")) {
            throw new PaymentFailureException("Card declined by issuing bank (simulated).");
        }
        return true;
    }

    @Override
    public String methodName() {
        return "CARD";
    }
}
