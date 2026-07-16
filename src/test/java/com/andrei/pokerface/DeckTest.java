package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

class DeckTest {

    @Test
    void freshDeckHas52Cards() {
        Deck deck = new Deck();
        assertEquals(52, deck.remainingCards());
    }

    @Test
    void allDeckCardsUnique() {
        Deck deck = new Deck();
        Set<Integer> seen = new HashSet<>();
        boolean uniqueSoFar = true;
        for (int i = 0; i < deck.remainingCards(); i++) {
            if (!seen.add(i)) {
                uniqueSoFar = false;
            }
        }
        assertSame(uniqueSoFar, true);
    }

    @Test
    void dealingFromEmptyDeckThrows() {
        Deck deck = new Deck();
        for (int i = 0; i < 52; i++) {
            deck.deal();
        }
        assertThrows(IllegalStateException.class, () -> deck.deal());
    }

    @Test
    void remainingDecrements() {
        Deck deck = new Deck();
        int prev = deck.remainingCards();
        deck.deal();
        int curr = deck.remainingCards();
        assertEquals(prev - 1, curr);
    }

}