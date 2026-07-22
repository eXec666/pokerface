package com.andrei.pokerface;

import java.util.List;

/**
 * Outcome of a RingGameRunner.runBatch() call: the net chip result of every
 * seat, for every hand played. Kept as raw per-hand samples (rather than only
 * a running total) so callers can compute not just a mean bb/100 but also its
 * standard error -- the whole point of this runner is to produce a metric
 * whose uncertainty can be quantified, not just a single number that looks
 * precise but isn't.
 */
public record RingGameResult(int handsPlayed, List<int[]> netChipsPerHand, int bigBlind) {

    public RingGameResult {
        netChipsPerHand = List.copyOf(netChipsPerHand);
    }

    /** Number of seats tracked, derived from the first recorded hand. Zero if no hands were recorded. */
    public int seatCount() {
        return netChipsPerHand.isEmpty() ? 0 : netChipsPerHand.get(0).length;
    }

    /** Mean net chips won per hand for the given seat, expressed in big blinds. */
    public double meanBbPerHand(int seatIndex) {
        double totalChips = 0;
        for (int[] hand : netChipsPerHand) {
            totalChips += hand[seatIndex];
        }
        return (totalChips / handsPlayed) / bigBlind;
    }

    /** Standard bot-performance metric: big blinds won per 100 hands. */
    public double bbPer100(int seatIndex) {
        return meanBbPerHand(seatIndex) * 100;
    }

    /**
     * Standard error of bbPer100(seatIndex), from the per-hand sample variance.
     * Report this alongside bbPer100 -- a bbPer100 figure without an error bar
     * is not a comparison between bots, just a number.
     */
    public double bbPer100StdError(int seatIndex) {
        double meanPerHandBb = meanBbPerHand(seatIndex);
        double sumSquaredDiff = 0;
        for (int[] hand : netChipsPerHand) {
            double bb = hand[seatIndex] / (double) bigBlind;
            double diff = bb - meanPerHandBb;
            sumSquaredDiff += diff * diff;
        }
        double variance = (handsPlayed > 1) ? sumSquaredDiff / (handsPlayed - 1) : 0.0;
        double stdErrorPerHand = Math.sqrt(variance / handsPlayed);
        return stdErrorPerHand * 100;
    }
}