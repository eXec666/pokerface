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

    // -------------------------------------------------------------------------
    // Multiway equity: monotonic decrease with opponent count
    // -------------------------------------------------------------------------

    @Test
    void estimateEquity_pocketAcesEquityDecreasesMonotonicallyWithMoreOpponents() {
        int[] holeCards = {0, 1}; // Ac, Ad
        int[] communityCards = new int[0];
        Random random = new Random(1);

        double previous = 1.01; // sentinel above any valid equity
        for (int numOpponents = 1; numOpponents <= 5; numOpponents++) {
            double equity = MonteCarloEquityEstimator.estimateEquity(
                    holeCards, communityCards, numOpponents, 5000, random);
            assertTrue(equity < previous,
                    "equity should strictly decrease as opponent count grows: "
                            + numOpponents + " opponents gave " + equity + ", previous was " + previous);
            previous = equity;
        }
    }

    @Test
    void estimateEquity_fiveOpponentsPocketAcesLandsNearKnownReference() {
        // AA vs 5 random hands has a well-documented equity near ~49%.
        int[] holeCards = {0, 1};
        int[] communityCards = new int[0];

        double equity = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 5, 20_000, new Random(3));

        assertTrue(equity > 0.44 && equity < 0.54,
                "expected AA vs 5 opponents equity near ~49%, got " + equity);
    }

    // -------------------------------------------------------------------------
    // Multiway tie-scoring: verifies the fraction is 1/(1 + tiedOpponents),
    // not 1/numOpponents
    // -------------------------------------------------------------------------

    @Test
    void estimateEquity_threeWayTieOnFullBoard_givesOneThirdCreditNotOneOverOpponentCount() {
        // Board is a royal flush in clubs using all 5 board cards: any hole cards
        // that don't include a higher club royal component tie for the board's
        // hand. With 2 opponents, if their hole cards also can't improve on the
        // board (guaranteed here since all 4 relevant club cards -- A,K,Q,J,T --
        // are already on the board or in the hero's hand), every player ties on
        // the board's royal flush -- a genuine 3-way tie (hero + 2 opponents),
        // so equity must be exactly 1/3, not 1/2 (which 1.0/numOpponents would
        // have wrongly produced only when numOpponents happened to equal the
        // tied-player count minus one).
        int[] holeCards = {5, 9}; // 2d, 3d -- irrelevant, board plays
        int[] communityCards = {0, 48, 44, 40, 36}; // Ac Kc Qc Jc Tc -- royal flush in clubs

        double equity = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 2, 1000, new Random(9));

        assertEquals(1.0 / 3.0, equity, 1e-9,
                "three-way tie on a fully-determined board must split equity evenly among all tied players");
    }

    @Test
    void estimateEquity_fullBoardWithOneOpponent_stillSplitsTiesInHalf() {
        // Regression guard: with exactly 1 opponent, a tie should still give
        // exactly 0.5 (matches the original heads-up ties/2.0 behavior) --
        // confirms the 1/(1+tiedOpponents) generalization reduces correctly
        // to the heads-up case rather than only being correct for larger N.
        int[] holeCards = {5, 9};
        int[] communityCards = {0, 48, 44, 40, 36};

        double equity = MonteCarloEquityEstimator.estimateEquity(
                holeCards, communityCards, 1, 1000, new Random(2));

        assertEquals(0.5, equity, 1e-9);
    }
}