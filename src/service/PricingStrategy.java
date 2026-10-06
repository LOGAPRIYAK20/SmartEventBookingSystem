package service;

import model.Event;

/**
 * Strategy pattern: any pricing algorithm plugs in here.
 * Demonstrates Abstraction + Interface + Polymorphism.
 */
public interface PricingStrategy {
    double calculatePrice(Event event, int seatsRequested);
}
