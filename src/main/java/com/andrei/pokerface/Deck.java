package com.andrei.pokerface;
import java.util.Random;

public class Deck {
    // Responsible for dealing and shuffling cards
    private final int[] cards;
    private int topIndex = 51;

    public Deck() {
        // initialize cards variable and populate with ints [0, 51]
        cards = new int[52];
        for (int i = 0; i < 52; i++) {
            cards[i] = i;
        }
    }

    /* Deck Methods */

    public void shuffle(int seed) {
        // shuffle the cards using the Fisher-Yates shuffle
        Random numgen = new Random(seed);
        for (int i = cards.length - 1; i > 0; i--) {
            int j = numgen.nextInt(i+1);
            int temp = cards[i];
            cards[i] = cards[j];
            cards[j] = temp;
        }
    }

    public int deal() {
        // Return the card at topIndex, and decrement topIndex by 1.
        if (topIndex < 0) {
            throw new IllegalStateException("Cannot deal from an empty deck");
        }
        int out = cards[topIndex];
        topIndex -= 1;
        return out;
    }

    /**
     * Discards the top card without returning it, mirroring live play "burning"
     * a card before the flop, turn, and river. Functionally equivalent to
     * calling deal() and ignoring the result, but named separately so intent
     * is explicit and a burned card is never even exposed to a caller.
     */
    public void burn() {
        if (topIndex < 0) {
            throw new IllegalStateException("Cannot burn from an empty deck");
        }
        topIndex -= 1;
    }

    public int remainingCards() {
        // return the number of remaining cards. 
        return topIndex + 1;
    }
    
    /**
     * Restores the deck to a full, unshuffled 52-card state so it can be reused
     * for the next hand. Callers should invoke shuffle(seed) immediately after
     * reset() if they want a fresh random order (as GameState.startNewHand does) --
     * reset() alone just refills and un-deals the cards.
     */
    public void reset() {
        for (int i = 0; i < 52; i++) {
            cards[i] = i;
        }
        topIndex = 51;
    }

}