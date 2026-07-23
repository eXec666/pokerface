package com.andrei.pokerface;

import java.util.List;

/**
 * Outcome of a TournamentBatchRunner.runBatch() call: one SessionResult per
 * tournament played. Aggregates into per-seat win counts/rates -- the
 * distribution a sensecheck run (e.g. every seat running the same bot) should
 * show as roughly uniform across seats. "Seat index" here means index into
 * the player list a given tournament's playerFactory produced, which by
 * TournamentBatchRunner's contract always matches the agents list's indexing.
 */
public record TournamentBatchResult(List<SessionResult> sessionResults) {

    public TournamentBatchResult {
        sessionResults = List.copyOf(sessionResults);
    }

    public int tournamentsPlayed() {
        return sessionResults.size();
    }

    /** Tournaments that hit the hand cap with more than one player still live, so no seat could be credited. */
    public long inconclusiveCount() {
        return sessionResults.stream().filter(r -> r.winner().isEmpty()).count();
    }

    /**
     * Win counts indexed by seat. Only tournaments with a decisive winner
     * contribute -- an inconclusive tournament has no winner to attribute.
     */
    public int[] winCountsBySeat(int seatCount) {
        int[] counts = new int[seatCount];
        for (SessionResult result : sessionResults) {
            result.winner().ifPresent(w -> counts[w.getSeatIndex()]++);
        }
        return counts;
    }

    /** Win rate per seat, as a fraction of ALL tournaments played (inconclusive tournaments count in the denominator). */
    public double[] winRatesBySeat(int seatCount) {
        int[] counts = winCountsBySeat(seatCount);
        double[] rates = new double[seatCount];
        for (int i = 0; i < seatCount; i++) {
            rates[i] = (double) counts[i] / tournamentsPlayed();
        }
        return rates;
    }

    public double averageHandsPlayed() {
        return sessionResults.stream().mapToInt(SessionResult::handsPlayed).average().orElse(0);
    }
}