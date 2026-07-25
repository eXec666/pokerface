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

    public static double estimateEquity(int[] holeCards, int[] communityCards, int numOpponents, int trials, Random random) {
        int wins = 0;
        int ties = 0;
        

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
        int [] opponentHole = new int[2];
        int[] trialCommunity = new int[5];

        // Run trials
        for (int trial = 0; trial < trials; trial++) {

            // Shuffle remaining cards
            fisherYates(remainingCards, random);
            
            // Deal opponent hole cards
            opponentHole[0] = remainingCards[0];
            opponentHole[1] = remainingCards[1];

            // Deal remaining community cards
            int knownCount = communityCards.length;
            int cardsNeeded = 5 - knownCount;
            System.arraycopy(communityCards, 0, trialCommunity, 0, knownCount);
            for (int i = 0; i < cardsNeeded; i++) {
                trialCommunity[knownCount + i] = remainingCards[2 + i];
            }

            // combine player hole + trialCommunity & opponent hole + trialCommunity
            int[] ownHand = new int[holeCards.length + trialCommunity.length];
            System.arraycopy(holeCards, 0, ownHand, 0, holeCards.length);
            System.arraycopy(trialCommunity, 0, ownHand, holeCards.length, trialCommunity.length);
            int[] opponentHand = new int[opponentHole.length + trialCommunity.length];
            System.arraycopy(opponentHole, 0, opponentHand, 0, opponentHole.length);
            System.arraycopy(trialCommunity, 0, opponentHand, opponentHole.length, trialCommunity.length);

            // evaluate hands
            int ownRank = HandEvaluator.evaluateBestHand(ownHand);
            int opponentRank = HandEvaluator.evaluateBestHand(opponentHand);
            // assign win/tie/loss
            if (ownRank > opponentRank) {
                wins++; 
            } else if (ownRank == opponentRank) {
                ties++;
            }
        }
        // compute weighted avg: win gets 1 point, tie gets 0.5 points, loss gets 0 points
        double totalScore = wins + (ties / 2.0);
        // compute equity: total score / num trials
        return totalScore / trials;
    }
}
