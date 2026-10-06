package service;

/**
 * Factory Pattern implementation for instantiating PaymentMethod instances.
 * Demonstrates: Factory Design Pattern, Switch Expressions (Java 21).
 */
public class PaymentMethodFactory {

    public static PaymentMethod create(String type) {
        if (type == null) return new UpiPayment();
        return switch (type.trim().toUpperCase()) {
            case "CARD" -> new CardPayment();
            case "NETBANKING", "NET_BANKING", "NET BANKING" -> new NetBankingPayment();
            case "UPI" -> new UpiPayment();
            default -> new UpiPayment();
        };
    }
}
