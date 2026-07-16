package com.andrei.pokerface;

/** A single small/big blind pairing in effect for some stretch of a session. */
public record BlindLevel(int smallBlind, int bigBlind) {
    public BlindLevel {
        if (smallBlind <= 0 || bigBlind <= 0) {
            throw new IllegalArgumentException("Blinds must be positive");
        }
        if (bigBlind < smallBlind) {
            throw new IllegalArgumentException("Big blind cannot be smaller than the small blind");
        }
    }
}