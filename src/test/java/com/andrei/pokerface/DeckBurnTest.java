package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeckBurnTest {

    @Test
    void burn_decrementsRemainingCardsByOne() {
        Deck deck = new Deck();
        int before = deck.remainingCards();
        deck.burn();
        assertEquals(before - 1, deck.remainingCards());
    }

    @Test
    void burn_consumesTheTopCardSoTheNextDealSkipsIt() {
        // Fresh, unshuffled deck deals descending: deal() would return 51 first.
        // Burning that card means the next deal() should return 50 instead.
        Deck deck = new Deck();
        deck.burn();
        assertEquals(50, deck.deal());
    }

    @Test
    void burn_multipleTimesConsumesMultipleCards() {
        Deck deck = new Deck();
        deck.burn();
        deck.burn();
        deck.burn();
        assertEquals(49, deck.remainingCards());
        assertEquals(48, deck.deal()); // 51, 50, 49 burned; next real card is 48
    }

    @Test
    void burn_onEmptyDeckThrows() {
        Deck deck = new Deck();
        for (int i = 0; i < 52; i++) {
            deck.deal();
        }
        assertThrows(IllegalStateException.class, deck::burn);
    }

    @Test
    void burn_interleavedWithDealBothAdvanceTopIndex() {
        Deck deck = new Deck();
        deck.deal();  // consumes 51
        deck.burn();  // consumes 50
        assertEquals(49, deck.deal());
        assertEquals(49, deck.remainingCards());
    }
}