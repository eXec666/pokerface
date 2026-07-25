package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

public class MonteCarloEquityEstimatorTest {

    /*
     * Card encoding reminder (consistent with HandEvalTest):
     *   card = (rank - 1) * 4 + (suit - 1)
     *   rank: 1=Ace, 2-13=Two-King
     *   suit: 1=clubs, 2=diamonds, 3=hearts, 4=spades
     *
     * Ac=0, Kc=48, Qc=44, Jc=40, Tc=36, 2d=5, 3h=10
     */

    // -------------------------------------------------------------------------
    // Sanity check 1: pocket aces vs. a random hand, empty board
    // -------------------------------------------------------------------------

    @Test
    void estimateEquity_pocketAcesPreflopHeadsUp_landsInKnownRange() {
        // AA vs. a uniformly random hand heads-up preflop has a well-known
        // equity of roughly 85%. This is a sanity check on the whole pipeline,
        // not an exact-value check -- Monte Carlo estimates carry sampling
        // noise, so we assert a generous band rather than a precise figure.
        int[] holeCards = {0, 1}; // Ac, Ad
        int[] communityCards = new int[0];

        double equity = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 1, 20_000, new Random(7));

        assertTrue(equity > 0.80 && equity < 0.90,
                "expected AA heads-up equity near ~85%, got " + equity);
    }

    @Test
    void estimateEquity_pocketAcesPreflopHeadsUp_isReproducibleForAFixedSeed() {
        // Same inputs, same seed value -> identical estimate every time. This
        // guards against accidental nondeterminism (e.g. shared mutable state
        // leaking between calls) rather than checking the equity value itself.
        int[] holeCards = {0, 1};
        int[] communityCards = new int[0];

        double first = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 1, 5_000, new Random(99));
        double second = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 1, 5_000, new Random(99));

        assertEquals(first, second, 1e-12,
                "identical seed and inputs must produce an identical equity estimate");
    }

    // -------------------------------------------------------------------------
    // Sanity check 2: fully dealt board, unbeatable hand -> deterministic 1.0
    // -------------------------------------------------------------------------

    @Test
    void estimateEquity_unbeatableHandWithFullBoard_isExactlyOneRegardlessOfTrials() {
        // Player holds Ac, Kc; board completes Qc, Jc, Tc, 2d, 3h -- a royal
        // flush in clubs using all five club cards needed for it. Every club
        // required for that royal (Ac, Kc, Qc, Jc, Tc) is already accounted
        // for between the player's hand and the board, so no possible 2-card
        // opponent hand can tie (a different-suit royal needs both hole cards
        // to be exactly that suit's A/K, and no board help remains for it) or
        // beat a royal flush. With cardsNeeded=0 (a full board), only the
        // opponent's hole cards vary per trial, and that variation is
        // irrelevant here since nothing beats this hand -- equity must be an
        // exact 1.0, at any trial count.
        int[] holeCards = {0, 48}; // Ac, Kc
        int[] communityCards = {44, 40, 36, 5, 10}; // Qc, Jc, Tc, 2d, 3h

        double equitySmall = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 1, 1, new Random(1));
        double equityLarge = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 1, 5_000, new Random(2));

        assertEquals(1.0, equitySmall, 1e-12);
        assertEquals(1.0, equityLarge, 1e-12);
    }
}