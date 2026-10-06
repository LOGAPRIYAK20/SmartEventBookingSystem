package service;

import exception.PaymentFailureException;

/**
 * Abstraction for different payment channels (Card, UPI, Wallet...).
 * Each implementation validates its own credentials -> Polymorphism.
 */
public interface PaymentMethod {
    /** Returns true if payment succeeds; throws PaymentFailureException on invalid input. */
    boolean pay(double amount, String credential) throws PaymentFailureException;

    String methodName();
}
