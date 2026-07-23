package com.andrei.pokerface;

import java.util.List;

/** One bot's aggregated ring-game performance, pooled across every seat it occupied. */
public record BotStatLine(String name, int handsPlayed, double bbPer100, double bbPer100StdError) {

    /** Builds a stat line from raw per-hand bb samples (already in big blinds, one value per hand played). */
    public static BotStatLine fromSamples(String name, List<Double> bbSamples) {
        int hands = bbSamples.size();
        double mean = bbSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double sumSquaredDiff = 0;
        for (double v : bbSamples) {
            double diff = v - mean;
            sumSquaredDiff += diff * diff;
        }
        double variance = (hands > 1) ? sumSquaredDiff / (hands - 1) : 0.0;
        double stdErrorPerHand = Math.sqrt(variance / Math.max(hands, 1));

        return new BotStatLine(name, hands, mean * 100, stdErrorPerHand * 100);
    }
}