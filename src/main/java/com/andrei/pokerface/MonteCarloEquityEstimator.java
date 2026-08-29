package com.andrei.pokerface;
import java.util.Random;

/*Monte Carlo Estimator for hand equity. */
public class MonteCarloEquityEstimator {

    // array membership helper
    private static boolean contains(int[] arr, int value) {
        for (int i : arr) {
            if (i == value) {
                return true;
            }
        }
        return false;
    }

    // Fisher-Yates shuffle helper
    private static void fisherYates(int[] arr, Random random) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = arr[i];
            arr[i] = arr[j];
            arr[j] = temp;
        }
    }

    private static double scoreTrial(int ownRank, int[] opponentRanks) {
    int bestOppScore = Integer.MIN_VALUE;
    for (int rank : opponentRanks) {
        if (rank > bestOppScore) {
            bestOppScore = rank;
        }
    }
    if (ownRank > bestOppScore) {
        return 1.0; // Win outright
    } else if (ownRank < bestOppScore) {
        return 0.0; // Someone strictly beats you
    } else {
        // ownRank == bestOppScore: you're tied for the best hand.
        // Count how many opponents share that top score, and split the
        // pot evenly among yourself and all of them.
        int tiedOpponents = 0;
        for (int rank : opponentRanks) {
            if (rank == bestOppScore) {
                tiedOpponents++;
            }
        }
        return 1.0 / (1 + tiedOpponents);
    }
}

    public static double estimateEquity(int[] holeCards, int[] communityCards, int numOpponents, int trials, Random random) {
        double totalScore = 0.0;
        // Build deck of remaining cards (once per hand)
        int writeIndex = 0;
        int[] remainingCards = new int[52 - holeCards.length - communityCards.length];
        for (int i = 0; i < 52; i++) {
            if(!contains(holeCards, i) && !contains(communityCards, i)) {
                remainingCards[writeIndex] = i;
                writeIndex++;
            }
        }

        // instantiate opponent hole cards & community cards for each trial
        int [] opponentHoleCards = new int[2 * numOpponents];
        int[] trialCommunity = new int[5];
        int [] opponentHand = new int[2 + 5]; // 2 hole + 5 community
        int [] opponentRanks = new int[numOpponents];

        // Run trials
        for (int trial = 0; trial < trials; trial++) {

            // Shuffle remaining cards
            fisherYates(remainingCards, random);
            
            // Deal opponent hole cards
            for (int i = 0; i < numOpponents; i++) {
                opponentHoleCards[2 * i] = remainingCards[2 * i];
                opponentHoleCards[2 * i + 1] = remainingCards[2 * i + 1];
            }

            // Deal remaining community cards
            int knownCount = communityCards.length;
            int cardsNeeded = 5 - knownCount;
            int boardStartIndex = 2 * numOpponents;
            System.arraycopy(communityCards, 0, trialCommunity, 0, knownCount);
            for (int i = 0; i < cardsNeeded; i++) {
                trialCommunity[knownCount + i] = remainingCards[boardStartIndex + i];
            }

            // combine player hole + trialCommunity, then evaluate hand.
            int[] ownHand = new int[holeCards.length + trialCommunity.length];
            System.arraycopy(holeCards, 0, ownHand, 0, holeCards.length);
            System.arraycopy(trialCommunity, 0, ownHand, holeCards.length, trialCommunity.length);
            int ownRank = HandEvaluator.evaluateBestHand(ownHand);

            
            for (int i = 0; i < numOpponents; i++) {
                opponentHand[0] = opponentHoleCards[2 * i];
                opponentHand[1] = opponentHoleCards[2 * i + 1];
                System.arraycopy(trialCommunity, 0, opponentHand, 2, trialCommunity.length);
                opponentRanks[i] = HandEvaluator.evaluateBestHand(opponentHand);
            }
            // score this trial and accumulate
            totalScore += scoreTrial(ownRank, opponentRanks);
        }
        // compute equity: total score / num trials
        return totalScore / trials;
    }
}
