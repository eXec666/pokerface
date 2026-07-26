package com.andrei.pokerface;

import java.util.Random;

public class DiagnoseEquityEstimates {
    public static void main(String[] args) {
        int[] communityCards = new int[0]; // preflop, matches the AllIn scenario exactly
        int trials = 5000;
        Random random = new Random(1);

        // A spread from very weak to very strong, to see the actual shape
        // of the estimator's output across the hand-strength range.
        int[][] hands = {
                {4, 12},   // 2c, 4c  -- weak, unsuited, no connection
                {5, 12},   // 2d, 4c  -- same, different suits
                {4, 9},    // 2c, 3d  -- weak, low connector
                {0, 44},   // Ac, Qc  -- suited ace, decent
                {0, 1},    // Ac, Ad  -- pocket aces, known ~0.85 vs random
                {48, 49},  // Kc, Kd  -- pocket kings, known strong
        };
        String[] labels = {"2c4c", "2d4c", "2c3d", "AcQc", "AcAd(AA)", "KcKd(KK)"};

        for (int i = 0; i < hands.length; i++) {
            double equity = MonteCarloEquityEstimator.estimateEquity(
                    hands[i], communityCards, 1, trials, random);
            System.out.println(labels[i] + " -> equity=" + equity);
        }
    }
}