package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HandEvalTest {

    /*
     * Card encoding reminder:
     *   card = (rank - 1) * 4 + (suit - 1)
     *   rank: 1=Ace, 2-13=Two-King
     *   suit: 1=clubs, 2=diamonds, 3=hearts, 4=spades
     *
     * Reference constants used throughout:
     *   Clubs:    Ac=0,  2c=4,  3c=8,  4c=12, 5c=16, 6c=20, 7c=24, 8c=28, 9c=32, Tc=36, Jc=40, Qc=44, Kc=48
     *   Diamonds: Ad=1,  2d=5,  3d=9,  4d=13, 5d=17,                      Td=37, Jd=41, Qd=45, Kd=49
     *   Hearts:   Ah=2,  2h=6,  3h=10,                                                    Qh=46, Kh=50
     *   Spades:   As=3,                                                                           Ks=51
     */
 
    // -------------------------------------------------------------------------
    // evaluateHighCard
    // -------------------------------------------------------------------------
 
    @Test
    void highCard_returnsTop5PokerValues() {
        // Ac(14), 7c(7), 6c(6), 5c(5), 4c(4), 3c(3), 2c(2)
        int[] cards = {0, 24, 20, 16, 12, 8, 4};
        int[] result = HandEvaluator.evaluateHighCard(cards);
        assertEquals(5, result.length);
        // Top card should be ace (14)
        assertEquals(14, result[0]);
        // Lowest of top 5 should be 4 (2 and 3 are excluded)
        assertEquals(4, result[result.length - 1]);
    }
 
    @Test
    void highCard_aceCountsAs14() {
        // Ac(14), Kc(13), Qc(12), Jc(11), Tc(10), 9c(9), 2c(2)
        int[] cards = {0, 48, 44, 40, 36, 32, 4};
        int[] result = HandEvaluator.evaluateHighCard(cards);
        assertEquals(14, result[0]);
        // 2 should be excluded from top 5; lowest should be T(10)
        assertEquals(10, result[result.length - 1]);
    }
 
    @Test
    void highCard_correctlyExcludesBottom2() {
        // Ac(14), Kc(13), Qc(12), Jc(11), Tc(10), 9c(9), 8c(8)
        int[] cards = {0, 48, 44, 40, 36, 32, 28};
        int[] result = HandEvaluator.evaluateHighCard(cards);
        // 8 and 9 should not appear in top 5
        for (int v : result) {
            assertNotEquals(8, v);
            assertNotEquals(9, v);
        }
        assertEquals(14, result[0]);
        assertEquals(10, result[result.length - 1]);
    }
 
    // -------------------------------------------------------------------------
    // evaluatePair
    // -------------------------------------------------------------------------
 
    @Test
    void pair_noPairReturnsSentinel() {
        // Ac, Kc, Qc, Jc, Tc, 9c, 8c — all different ranks
        int[] cards = {0, 48, 44, 40, 36, 32, 28};
        assertEquals(0, HandEvaluator.evaluatePair(cards).length);
    }
 
    @Test
    void pair_detectsHighestPairRank() {
        // Pair of aces: Ac(0), Ad(1), Kc(48), Qc(44), Jc(40), Tc(36), 9c(32)
        int[] cards = {0, 1, 48, 44, 40, 36, 32};
        int[] result = HandEvaluator.evaluatePair(cards);
        assertEquals(4, result.length);
        assertEquals(14, result[0]);
    }
 
    @Test
    void pair_correctKickersDescending() {
        // Pair of aces + Kc, Qc, Jc, Tc, 9c
        // kickers should be K(13), Q(12), J(11)
        int[] cards = {0, 1, 48, 44, 40, 36, 32};
        int[] result = HandEvaluator.evaluatePair(cards);
        assertEquals(14, result[0]);
        assertEquals(13, result[1]);
        assertEquals(12, result[2]);
        assertEquals(11, result[3]);
    }
 
    @Test
    void pair_selectsHigherOfTwoPairsPresent() {
        // Aces and Kings both paired — pair method should return aces
        int[] cards = {0, 1, 48, 49, 44, 40, 36};
        int[] result = HandEvaluator.evaluatePair(cards);
        assertEquals(14, result[0]);
    }
 
    @Test
    void pair_acePairBeatsKingPair() {
        int[] acePair   = {0, 1, 48, 44, 40, 36, 32};
        int[] kingPair  = {48, 49, 44, 40, 36, 32, 28};
        assertTrue(HandEvaluator.evaluatePair(acePair)[0]
                 > HandEvaluator.evaluatePair(kingPair)[0]);
    }
 
    // -------------------------------------------------------------------------
    // evaluateTwoPair
    // -------------------------------------------------------------------------
 
    @Test
    void twoPair_noTwoPairReturnsSentinel() {
        // Only one pair (aces), five other distinct ranks
        int[] cards = {0, 1, 48, 44, 40, 36, 32};
        assertEquals(0, HandEvaluator.evaluateTwoPair(cards).length);
    }
 
    @Test
    void twoPair_noPairAtAllReturnsSentinel() {
        int[] cards = {0, 48, 44, 40, 36, 32, 28};
        assertEquals(0, HandEvaluator.evaluateTwoPair(cards).length);
    }
 
    @Test
    void twoPair_detectsTopTwoPairs() {
        // Ac(0), Ad(1), Kc(48), Kd(49), Qc(44), Jc(40), Tc(36)
        int[] cards = {0, 1, 48, 49, 44, 40, 36};
        int[] result = HandEvaluator.evaluateTwoPair(cards);
        assertEquals(3, result.length);
        assertEquals(14, result[0]); // high pair = aces
        assertEquals(13, result[1]); // low pair = kings
        assertEquals(12, result[2]); // kicker = queen
    }
 
    @Test
    void twoPair_threePairsSelectsTopTwoAndCorrectKicker() {
        // Ac(0), Ad(1), Kc(48), Kd(49), Qc(44), Qd(45), Jc(40)
        // Three pairs: aces(14), kings(13), queens(12)
        // Should select aces + kings; kicker = queen (one queen still free)
        int[] cards = {0, 1, 48, 49, 44, 45, 40};
        int[] result = HandEvaluator.evaluateTwoPair(cards);
        assertEquals(14, result[0]);
        assertEquals(13, result[1]);
        assertEquals(12, result[2]); // demoted queen pair contributes a kicker
    }
 
    @Test
    void twoPair_kickerIsHighestNonPairCard() {
        // Ac(0), Ad(1), Kc(48), Kd(49), Tc(36), 9c(32), 8c(28)
        // pairs: aces(14), kings(13) — kicker should be T(10) not 9 or 8
        int[] cards = {0, 1, 48, 49, 36, 32, 28};
        int[] result = HandEvaluator.evaluateTwoPair(cards);
        assertEquals(10, result[2]);
    }
 
    // -------------------------------------------------------------------------
    // evaluateTriple
    // -------------------------------------------------------------------------
 
    @Test
    void triple_noTripleReturnsSentinel() {
        // Only a pair of aces
        int[] cards = {0, 1, 48, 44, 40, 36, 32};
        assertEquals(0, HandEvaluator.evaluateTriple(cards).length);
    }
 
    @Test
    void triple_detectsTripAces() {
        // Ac(0), Ad(1), Ah(2), Kc(48), Qc(44), Jc(40), Tc(36)
        int[] cards = {0, 1, 2, 48, 44, 40, 36};
        int[] result = HandEvaluator.evaluateTriple(cards);
        assertEquals(3, result.length);
        assertEquals(14, result[0]);
    }
 
    @Test
    void triple_correctKickers() {
        // Trip aces + Kc, Qc, Jc, Tc — top 2 kickers should be K(13), Q(12)
        int[] cards = {0, 1, 2, 48, 44, 40, 36};
        int[] result = HandEvaluator.evaluateTriple(cards);
        assertEquals(13, result[1]);
        assertEquals(12, result[2]);
    }
 
    @Test
    void triple_kickersDoNotIncludeTripRank() {
        // Trip aces + pair kings — kickers must not be aces
        int[] cards = {0, 1, 2, 48, 49, 44, 40};
        int[] result = HandEvaluator.evaluateTriple(cards);
        assertNotEquals(14, result[1]);
        assertNotEquals(14, result[2]);
    }
 
    @Test
    void triple_selectsHighestTripWhenTwoTripsPresent() {
        // Trip aces + trip kings: Ac(0), Ad(1), Ah(2), Kc(48), Kd(49), Ks(51), Qc(44)
        int[] cards = {0, 1, 2, 48, 49, 51, 44};
        int[] result = HandEvaluator.evaluateTriple(cards);
        assertEquals(14, result[0]); // aces win
    }
 
    // -------------------------------------------------------------------------
    // evaluateStraight
    // -------------------------------------------------------------------------
 
    @Test
    void straight_noStraightReturnsSentinel() {
        // A,K,Q,J,9,8,2 — missing T for broadway, no other 5-run
        int[] cards = {0, 48, 44, 40, 32, 28, 4};
        assertEquals(0, HandEvaluator.evaluateStraight(cards).length);
    }
 
    @Test
    void straight_broadway() {
        // Ac(0), Kc(48), Qc(44), Jc(40), Tc(36), 2c(4), 3c(8)
        int[] cards = {0, 48, 44, 40, 36, 4, 8};
        int[] result = HandEvaluator.evaluateStraight(cards);
        assertEquals(1, result.length);
        assertEquals(14, result[0]);
    }
 
    @Test
    void straight_wheel() {
        // Ac(0), 2c(4), 3c(8), 4c(12), 5c(16), 7c(24), 9c(32)
        int[] cards = {0, 4, 8, 12, 16, 24, 32};
        int[] result = HandEvaluator.evaluateStraight(cards);
        assertEquals(1, result.length);
        assertEquals(5, result[0]); // wheel top card = 5
    }
 
    @Test
    void straight_nineHigh() {
        // 5c(16), 6c(20), 7c(24), 8c(28), 9c(32), Ac(0), Kc(48)
        int[] cards = {16, 20, 24, 28, 32, 0, 48};
        int[] result = HandEvaluator.evaluateStraight(cards);
        assertEquals(9, result[0]);
    }
 
    @Test
    void straight_prefersHigherStraightWhenMultiplePresent() {
        // 6c(20), 7c(24), 8c(28), 9c(32), Tc(36), Jc(40), 2c(4)
        // Both 9-high and J-high straights exist; J-high should win
        int[] cards = {20, 24, 28, 32, 36, 40, 4};
        int[] result = HandEvaluator.evaluateStraight(cards);
        assertEquals(11, result[0]);
    }
 
    @Test
    void straight_duplicateRanksDontCreateFalseStraight() {
        // Paired aces, kings, queens, jacks + 9 — missing T, no straight
        int[] cards = {0, 1, 48, 49, 44, 45, 32};
        assertEquals(0, HandEvaluator.evaluateStraight(cards).length);
    }
 
    @Test
    void straight_wheelNotReturnedWhenHigherStraightExists() {
        // A-2-3-4-5 present but also 2-3-4-5-6 present — 6-high wins
        // Ac(0), 2c(4), 3c(8), 4c(12), 5c(16), 6c(20), Kc(48)
        int[] cards = {0, 4, 8, 12, 16, 20, 48};
        int[] result = HandEvaluator.evaluateStraight(cards);
        assertEquals(6, result[0]);
    }
 
    // -------------------------------------------------------------------------
    // evaluateFlush
    // -------------------------------------------------------------------------
 
    @Test
    void flush_noFlushReturnsSentinel() {
        // Only 4 clubs: Ac(0), Kc(48), Qc(44), Jc(40), Td(37), 9d(33), 8h(30)
        int[] cards = {0, 48, 44, 40, 37, 33, 30};
        assertEquals(0, HandEvaluator.evaluateFlush(cards).length);
    }
 
    @Test
    void flush_detectsExactlyFiveOfSameSuit() {
        // Ac(0), Kc(48), Qc(44), Jc(40), Tc(36), 2d(5), 3h(10)
        int[] cards = {0, 48, 44, 40, 36, 5, 10};
        int[] result = HandEvaluator.evaluateFlush(cards);
        assertEquals(5, result.length);
    }
 
    @Test
    void flush_returnsTop5When6Suited() {
        // 6 clubs: Ac(0), Kc(48), Qc(44), Jc(40), Tc(36), 2c(4), 3d(9)
        // 2c should be excluded — lowest of the 6 clubs
        int[] cards = {0, 48, 44, 40, 36, 4, 9};
        int[] result = HandEvaluator.evaluateFlush(cards);
        assertEquals(5, result.length);
        for (int v : result) {
            assertNotEquals(2, v);
        }
    }
 
    @Test
    void flush_aceHighFlushTopCard() {
        // Flush: Ac, Kc, Qc, Jc, Tc
        int[] cards = {0, 48, 44, 40, 36, 5, 10};
        int[] result = HandEvaluator.evaluateFlush(cards);
        // First element should be 14 (descending order)
        assertEquals(14, result[0]);
    }
 
    @Test
    void flush_valuesAreDescending() {
        // Ac(0), Kc(48), Qc(44), Jc(40), Tc(36), 2d(5), 3h(10)
        int[] cards = {0, 48, 44, 40, 36, 5, 10};
        int[] result = HandEvaluator.evaluateFlush(cards);
        for (int i = 0; i < result.length - 1; i++) {
            assertTrue(result[i] >= result[i + 1],
                "Expected descending order at index " + i);
        }
    }
 
    // -------------------------------------------------------------------------
    // evaluateFullHouse
    // -------------------------------------------------------------------------
 
    @Test
    void fullHouse_noTripReturnsSentinel() {
        // Only pairs — no trip
        int[] cards = {0, 1, 48, 49, 44, 40, 36};
        assertEquals(0, HandEvaluator.evaluateFullHouse(cards).length);
    }
 
    @Test
    void fullHouse_tripButNoPairReturnsSentinel() {
        // Trip aces, all other cards distinct ranks
        int[] cards = {0, 1, 2, 48, 44, 40, 36};
        assertEquals(0, HandEvaluator.evaluateFullHouse(cards).length);
    }
 
    @Test
    void fullHouse_normalCase() {
        // Trip aces + pair kings: Ac(0), Ad(1), Ah(2), Kc(48), Kd(49), Qc(44), Jc(40)
        int[] cards = {0, 1, 2, 48, 49, 44, 40};
        int[] result = HandEvaluator.evaluateFullHouse(cards);
        assertEquals(2, result.length);
        assertEquals(14, result[0]); // trip rank
        assertEquals(13, result[1]); // pair rank
    }
 
    @Test
    void fullHouse_twoTripletsHigherTripWins() {
        // Trip aces + trip kings: Ac(0), Ad(1), Ah(2), Kc(48), Kd(49), Ks(51), Qc(44)
        int[] cards = {0, 1, 2, 48, 49, 51, 44};
        int[] result = HandEvaluator.evaluateFullHouse(cards);
        assertEquals(2, result.length);
        assertEquals(14, result[0]); // aces = higher triple
        assertEquals(13, result[1]); // kings reused as the pair component
    }
 
    @Test
    void fullHouse_acesFullBeatsKingsFull() {
        int[] acesFull = {0, 1, 2, 48, 49, 44, 40};  // AAA KK
        int[] kingsFull = {48, 49, 51, 0, 1, 44, 40}; // KKK AA
        int[] r1 = HandEvaluator.evaluateFullHouse(acesFull);
        int[] r2 = HandEvaluator.evaluateFullHouse(kingsFull);
        assertTrue(r1[0] > r2[0]);
    }
 
    // -------------------------------------------------------------------------
    // evaluateQuad
    // -------------------------------------------------------------------------
 
    @Test
    void quad_noQuadReturnsSentinel() {
        // Trip aces only — not a quad
        int[] cards = {0, 1, 2, 48, 44, 40, 36};
        assertEquals(0, HandEvaluator.evaluateQuad(cards).length);
    }
 
    @Test
    void quad_detectsQuadAces() {
        // Ac(0), Ad(1), Ah(2), As(3), Kc(48), Qc(44), Jc(40)
        int[] cards = {0, 1, 2, 3, 48, 44, 40};
        int[] result = HandEvaluator.evaluateQuad(cards);
        assertEquals(2, result.length);
        assertEquals(14, result[0]);
    }
 
    @Test
    void quad_selectsBestKicker() {
        // Quad aces + K, Q, J — kicker must be K(13)
        int[] cards = {0, 1, 2, 3, 48, 44, 40};
        int[] result = HandEvaluator.evaluateQuad(cards);
        assertEquals(13, result[1]);
    }
 
    @Test
    void quad_kickerNotFromQuadRank() {
        int[] cards = {0, 1, 2, 3, 48, 44, 40};
        int[] result = HandEvaluator.evaluateQuad(cards);
        assertNotEquals(14, result[1]);
    }
 
    // -------------------------------------------------------------------------
    // evaluateStraightFlush
    // NOTE: requires evaluateStraightFlush to call countSuits (not countRanks).
    // Tests will fail until that bug is fixed.
    // -------------------------------------------------------------------------
 
    @Test
    void straightFlush_noFlushReturnsSentinel() {
        // Mixed suits, no flush
        int[] cards = {0, 48, 44, 40, 37, 33, 30};
        assertEquals(0, HandEvaluator.evaluateStraightFlush(cards).length);
    }
 
    @Test
    void straightFlush_flushButNoStraightReturnsSentinel() {
        // 5 clubs but not consecutive: Ac(0), Kc(48), Qc(44), Jc(40), 2c(4), 3d(9), 4h(14)
        int[] cards = {0, 48, 44, 40, 4, 9, 14};
        assertEquals(0, HandEvaluator.evaluateStraightFlush(cards).length);
    }
 
    @Test
    void straightFlush_straightButAcrossMultipleSuitsReturnsSentinel() {
        // A-K-Q-J-T straight but T is diamonds: Ac(0), Kc(48), Qc(44), Jc(40), Td(37), 2c(4), 3c(8)
        // Clubs: A,K,Q,J,2,3 — no 5-card straight within clubs alone
        int[] cards = {0, 48, 44, 40, 37, 4, 8};
        assertEquals(0, HandEvaluator.evaluateStraightFlush(cards).length);
    }
 
    @Test
    void straightFlush_broadway() {
        // All in clubs: Ac(0), Kc(48), Qc(44), Jc(40), Tc(36), 2d(5), 3h(10)
        int[] cards = {0, 48, 44, 40, 36, 5, 10};
        int[] result = HandEvaluator.evaluateStraightFlush(cards);
        assertEquals(1, result.length);
        assertEquals(14, result[0]);
    }
 
    @Test
    void straightFlush_wheel() {
        // Ac(0), 2c(4), 3c(8), 4c(12), 5c(16), Kd(49), Qh(46)
        int[] cards = {0, 4, 8, 12, 16, 49, 46};
        int[] result = HandEvaluator.evaluateStraightFlush(cards);
        assertEquals(1, result.length);
        assertEquals(5, result[0]);
    }
 
    @Test
    void straightFlush_nineHigh() {
        // 5c(16), 6c(20), 7c(24), 8c(28), 9c(32), Ad(1), Kd(49)
        int[] cards = {16, 20, 24, 28, 32, 1, 49};
        int[] result = HandEvaluator.evaluateStraightFlush(cards);
        assertEquals(9, result[0]);
    }
 
    @Test
    void straightFlush_beatsRegularFlushAndStraight() {
        // Straight flush in clubs (9-high) vs regular flush only
        int[] sfCards  = {16, 20, 24, 28, 32, 1, 49}; // 5c-9c + off-suit
        int[] flushOnly = {0, 48, 44, 4, 8, 1, 49};    // A,K,Q,2,3 clubs — flush, no straight
        assertEquals(0, HandEvaluator.evaluateStraightFlush(flushOnly).length);
        assertEquals(1, HandEvaluator.evaluateStraightFlush(sfCards).length);
    }

    // -------------------------------------------------------------------------
// encodeScore
// -------------------------------------------------------------------------

@Test
void encodeScore_correctlyPacksHandTypeAndRanks() {
    // High card: A-K-Q-J-10 (ranks 14,13,12,11,10)
    int score = HandEvaluator.encodeScore(0, new int[]{14, 13, 12, 11, 10});
    // handType (0) << 20 = 0
    int expected = (14 << 16) | (13 << 12) | (12 << 8) | (11 << 4) | 10;
    assertEquals(expected, score);
}

@Test
void encodeScore_handTypeOverridesAllRanks() {
    // Pair of 2s (type 1) should beat high card Ace (type 0)
    int pairScore = HandEvaluator.encodeScore(1, new int[]{2, 14, 13, 12});
    int highScore  = HandEvaluator.encodeScore(0, new int[]{14, 13, 12, 11, 10});
    assertTrue(pairScore > highScore);
}

@Test
void encodeScore_padsMissingRanksWithZero() {
    // Two Pair only returns 3 values: highPair, lowPair, kicker
    int score = HandEvaluator.encodeScore(2, new int[]{14, 13, 12});
    // missing slots become 0
    int expected = (2 << 20) | (14 << 16) | (13 << 12) | (12 << 8);
    assertEquals(expected, score);
}

// -------------------------------------------------------------------------
// evaluateBestHand – ordering across all hand types
// -------------------------------------------------------------------------

@Test
void bestHand_straightFlushBeatsQuad() {
    // Straight flush: 9c-8c-7c-6c-5c + off-suit (type 8)
    int[] sf = {32, 28, 24, 20, 16, 1, 49}; 
    // Quad aces: Ac-Ad-Ah-As + Kc-Qc-Jc (type 7)
    int[] quad = {0, 1, 2, 3, 48, 44, 40};

    int scoreSF = HandEvaluator.evaluateBestHand(sf);
    int scoreQuad = HandEvaluator.evaluateBestHand(quad);
    assertTrue(scoreSF > scoreQuad);
}

@Test
void bestHand_quadBeatsFullHouse() {
    int[] quad = {0, 1, 2, 3, 48, 44, 40}; // AAAA K Q J
    int[] fh   = {0, 1, 2, 48, 49, 44, 40}; // AAA KK Q J
    assertTrue(HandEvaluator.evaluateBestHand(quad) >
               HandEvaluator.evaluateBestHand(fh));
}

@Test
void bestHand_fullHouseBeatsFlush() {
    // Full house: AAA KK
    int[] fullHouse = {0, 1, 2, 48, 49, 44, 40}; // Ac Ad Ah Kc Kd Qc Jc
    // Flush: clubs A-K-Q-J-9 (same as above, no straight)
    int[] flush = {0, 48, 44, 40, 32, 5, 10};
    assertTrue(HandEvaluator.evaluateBestHand(fullHouse) >
               HandEvaluator.evaluateBestHand(flush));
}

@Test
void bestHand_flushBeatsStraight() {
    // Flush: clubs A-K-Q-J-9 (no straight)
    int[] flush = {0, 48, 44, 40, 32, 5, 10}; // Ac Kc Qc Jc 9c + 2d 3h
    // Straight: A-K-Q-J-T with mixed suits (no flush)
    int[] straight = {0, 49, 46, 43, 36, 4, 8}; // Ac Kd Qh Js Tc + 2c 3c
    assertTrue(HandEvaluator.evaluateBestHand(flush) >
               HandEvaluator.evaluateBestHand(straight));
}


@Test
void bestHand_straightBeatsTriple() {
    // Straight: A-K-Q-J-T with mixed suits (no flush)
    int[] straight = {0, 49, 46, 43, 36, 4, 8}; // Ac Kd Qh Js Tc 2c 3c
    // Triple: AAA with mixed suits, no flush, no straight
    int[] triple = {0, 1, 2, 49, 46, 43, 33}; // Ac Ad Ah Kd Qh Js 9d
    assertTrue(HandEvaluator.evaluateBestHand(straight) >
               HandEvaluator.evaluateBestHand(triple));
}

@Test
void bestHand_tripleBeatsTwoPair() {
    // Triple: AAA with mixed suits, no straight, no flush
    int[] triple = {0, 1, 2, 51, 45, 40, 35}; // Ac Ad Ah Ks Qd Jc 9s
    // Two pair: AA KK with mixed suits, no straight, no flush
    int[] twoPair = {0, 1, 48, 49, 47, 40, 33}; // Ac Ad Kc Kd Qs Jc 9d
    assertTrue(HandEvaluator.evaluateBestHand(triple) >
               HandEvaluator.evaluateBestHand(twoPair));
}

@Test
void bestHand_twoPairBeatsPair() {
    // Two pair: AA KK (no flush)
    int[] twoPair = {0, 1, 48, 49, 47, 40, 33}; // Ac Ad Kc Kd Qs Jc 9d
    // Pair: AA with mixed suits (no flush)
    int[] pair = {0, 1, 51, 45, 40, 35, 28}; // Ac Ad Ks Qd Jc 9s 8c
    assertTrue(HandEvaluator.evaluateBestHand(twoPair) >
               HandEvaluator.evaluateBestHand(pair));
}

@Test
void bestHand_pairBeatsHighCard() {
    // Pair: AA K Q J 9 8
    int[] pair = {0, 1, 48, 44, 40, 32, 28};
    // High card: A K Q J 9 8 7 (all distinct, no straight, mixed suits)
    int[] high = {0, 49, 46, 43, 32, 30, 25}; // Ac Kd Qh Js 9c 8h 7d
    assertTrue(HandEvaluator.evaluateBestHand(pair) >
               HandEvaluator.evaluateBestHand(high));
}

// -------------------------------------------------------------------------
// evaluateBestHand – tie / kicker edge cases within same type
// -------------------------------------------------------------------------

@Test
void bestHand_pairAcesBeatsPairKings() {
    int[] aces = {0, 1, 48, 44, 40, 36, 32}; // AA K Q J T 9
    int[] kings = {48, 49, 44, 40, 36, 32, 28}; // KK Q J T 9 8
    assertTrue(HandEvaluator.evaluateBestHand(aces) >
               HandEvaluator.evaluateBestHand(kings));
}

@Test
void bestHand_twoPairAcesKingsBeatsAcesQueens() {
    // AA KK with Q kicker (no flush)
    int[] acesKings = {0, 1, 48, 49, 47, 40, 33}; // Ac Ad Kc Kd Qs Jc 9d
    // AA QQ with K kicker (no flush)
    int[] acesQueens = {0, 1, 44, 45, 51, 40, 33}; // Ac Ad Qc Qd Ks Jc 9d
    assertTrue(HandEvaluator.evaluateBestHand(acesKings) >
               HandEvaluator.evaluateBestHand(acesQueens));
}

@Test
void bestHand_fullHouseAcesOverKingsBeatsAcesOverQueens() {
    int[] aaaKK = {0, 1, 2, 48, 49, 44, 40}; // AAA KK
    int[] aaaQQ = {0, 1, 2, 44, 45, 48, 40}; // AAA QQ
    assertTrue(HandEvaluator.evaluateBestHand(aaaKK) >
               HandEvaluator.evaluateBestHand(aaaQQ));
}

@Test
void bestHand_aceHighStraightBeatsKingHighStraight() {
    int[] broadway = {0, 48, 44, 40, 36, 4, 8}; // A K Q J T
    int[] kingHigh = {48, 44, 40, 36, 32, 4, 8}; // K Q J T 9
    assertTrue(HandEvaluator.evaluateBestHand(broadway) >
               HandEvaluator.evaluateBestHand(kingHigh));
}

@Test
void bestHand_wheelStraightLosesToSixHighStraight() {
    int[] wheel = {0, 4, 8, 12, 16, 48, 44}; // A 2 3 4 5
    int[] sixHigh = {4, 8, 12, 16, 20, 48, 44}; // 2 3 4 5 6
    assertTrue(HandEvaluator.evaluateBestHand(sixHigh) >
               HandEvaluator.evaluateBestHand(wheel));
}

@Test
void bestHand_exactTieReturnsEqualScores() {
    // Royal flush in hearts vs royal flush in spades – suits don't matter
    int[] royalHearts = {2, 50, 46, 42, 38, 4, 8}; // Ah Kh Qh Jh Th
    int[] royalSpades = {3, 51, 47, 43, 39, 4, 8}; // As Ks Qs Js Ts
    assertEquals(HandEvaluator.evaluateBestHand(royalHearts),
                 HandEvaluator.evaluateBestHand(royalSpades));
}

@Test
void bestHand_pairWithBetterKickersWins() {
    // Both have pair of aces, but first has K,Q,J kickers vs K,Q,T
    int[] betterKickers = {0, 1, 48, 44, 40, 36, 32}; // AA K Q J T 9
    int[] worseKickers  = {0, 1, 48, 44, 36, 32, 28}; // AA K Q T 9 8
    assertTrue(HandEvaluator.evaluateBestHand(betterKickers) >
               HandEvaluator.evaluateBestHand(worseKickers));
}

@Test
void bestHand_twoPairSameHighPairKickerDecides() {
    // AA KK with Q kicker vs AA KK with T kicker
    int[] qKicker = {0, 1, 48, 49, 44, 40, 36}; // AA KK Q J T
    int[] tKicker = {0, 1, 48, 49, 36, 32, 28}; // AA KK T 9 8
    assertTrue(HandEvaluator.evaluateBestHand(qKicker) >
               HandEvaluator.evaluateBestHand(tKicker));
}
    
}