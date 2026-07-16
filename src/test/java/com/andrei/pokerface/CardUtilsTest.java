package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


class CardsUtilTest {

    @Test
    void rankLeftOOBThrows() {
        int card = -1;
        assertThrows(IllegalArgumentException.class, () -> CardUtils.getRank(card));
    }

    @Test
    void rankRightOOBThrows() {
        int card = 52;
        assertThrows(IllegalArgumentException.class, () -> CardUtils.getRank(card));
    }

    @Test
    void rankLeftBoundary() {
        int card = 0;
        int boundaryRank = CardUtils.getRank(card);
        assertEquals(boundaryRank, 1);
    }

    @Test
    void rankRightBoundary() {
        int card = 51;
        int boundaryRank = CardUtils.getRank(card);
        assertEquals(boundaryRank, 13);
    }

    @Test
    void suitLeftOOBThrows() {
        int card = -1;
        assertThrows(IllegalArgumentException.class, () -> CardUtils.getSuit(card));
    }

    @Test
    void suitRightOOBThrows() {
        int card = 52;
        assertThrows(IllegalArgumentException.class, () -> CardUtils.getSuit(card));
    }

    @Test
    void suitLeftBoundary() {
        int card = 0;
        int boundarySuit = CardUtils.getSuit(card);
        assertEquals(boundarySuit, 1);
    }

    @Test
    void suitRightBoundary() {
        int card = 51;
        int boundarySuit = CardUtils.getSuit(card);
        assertEquals(boundarySuit, 4);
    }

    @Test
    void acePokerValuesCorrect() {
        for (int i = 0; i < 4; i++) {
            assertEquals(CardUtils.pokerValue(i), 14);
        }
    }

    @Test
    void otherPokerValuesCorrect() {
        for (int i = 4; i < 52; i++) {
            assertEquals(CardUtils.pokerValue(i), CardUtils.getRank(i));
        }
    }

    @Test
void cardToString_convertsCorrectly() {
    assertEquals("Ac", CardUtils.cardToString(0));   // rank=1, suit=1
    assertEquals("2c", CardUtils.cardToString(4));   // rank=2, suit=1
    assertEquals("Ts", CardUtils.cardToString(39));  // rank=10, suit=4
    assertEquals("Kd", CardUtils.cardToString(49));  // rank=13, suit=2
    assertEquals("Ah", CardUtils.cardToString(2));   // rank=1, suit=3
}

@Test
void handToString_joinsWithCommas() {
    int[] cards = {0, 48, 44};
    assertEquals("Ac, Kc, Qc", CardUtils.handToString(cards));
}

@Test
void handToString_handlesEmptyArray() {
    assertEquals("", CardUtils.handToString(new int[0]));
    assertEquals("", CardUtils.handToString(null));
}

@Test
void handToStringWithCount_limitsOutput() {
    int[] cards = {0, 48, 44, 40};
    assertEquals("Ac, Kc, Qc", CardUtils.handToString(cards, 3));
}

}