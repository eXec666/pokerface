package com.andrei.pokerface;

import java.util.Collections;
import java.util.Set;

public class Pot {
    private int amount;
    private final Set<Integer> eligibleSeats; // seatIndex values of players who can win this pot

    public Pot(int amount, Set<Integer> eligibleSeats) {
        this.amount = amount;
        this.eligibleSeats = eligibleSeats;
    }

    public int getAmount() { return amount; }

    public void addAmount(int extra) {
        if (extra < 0) {
            throw new IllegalArgumentException("Cannot add a negative amount to a pot");
        }
        amount += extra;
    }

    public Set<Integer> getEligibleSeats() {
        return Collections.unmodifiableSet(eligibleSeats);
    }

    public boolean isEligible(int seatIndex) {
        return eligibleSeats.contains(seatIndex);
    }

    @Override
    public String toString() {
        return "Pot{amount=" + amount + ", eligibleSeats=" + eligibleSeats + "}";
    }
}