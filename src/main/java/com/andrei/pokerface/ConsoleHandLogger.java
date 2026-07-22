package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints a human-readable narration to stdout: hand boundaries, and at
 * showdown, every non-folded player's hole cards alongside their best hand
 * description, followed by the pot award(s). Distinct from FileHandLogger's
 * terse one-line-per-event format, which is built for parsing, not reading
 * live.
 *
 * Needs its own copy of the community board, since GameEvent never carries
 * it as a whole -- it's rebuilt incrementally from CommunityCardDealt events
 * and reset on every HandStarted. Needs the live Player list to read hole
 * cards and fold status, since hole cards are never logged as events at all
 * (see GameState.dealHoleCards). This is safe only because SessionRunner
 * keeps the same Player instances alive across every GameState rebuild in a
 * session -- hole cards are cleared solely by resetForNewHand(), which the
 * *next* hand's startNewHand() triggers, always after this logger has
 * already read them via the current hand's HandEnded event.
 */
public class ConsoleHandLogger implements HandLogger {
    private final List<Player> players;
    private final List<Integer> board = new ArrayList<>();
    private final List<GameEvent.PotAwarded> potsThisHand = new ArrayList<>();

    public ConsoleHandLogger(List<Player> players) {
        this.players = players;
    }

    @Override
    public void log(GameEvent event) {
        switch (event) {
            case GameEvent.HandStarted e -> {
                board.clear();
                potsThisHand.clear();
                System.out.println("\n===== New hand (dealer seat " + e.dealerSeat() + ") =====");
            }
            case GameEvent.CommunityCardDealt e -> board.add(e.card());
            case GameEvent.PotAwarded e -> potsThisHand.add(e);
            case GameEvent.HandEnded e -> printShowdown(e);
            default -> { /* blinds/actions/board are already visible via HumanCliAgent's render */ }
        }
    }

    private void printShowdown(GameEvent.HandEnded e) {
        if (e.wonByFold()) {
            int total = potsThisHand.stream().mapToInt(GameEvent.PotAwarded::amount).sum();
            System.out.println("Hand won by fold -- seat(s) " + e.winnerSeats() + " take " + total + " chips.");
            return;
        }

        System.out.println("---- Showdown ----");
        int[] boardCards = board.stream().mapToInt(Integer::intValue).toArray();
        for (Player p : players) {
            if (p.isFolded() || p.getHoleCards()[0] < 0) {
                continue; // folded, or never dealt into this hand at all
            }
            int[] combined = combine(p.getHoleCards(), boardCards);
            String description = HandEvaluator.describeBestHand(combined);
            System.out.println("  Seat " + p.getSeatIndex() + " " + p.getName() + ": "
                    + CardUtils.handToString(p.getHoleCards()) + "  ->  " + description);
        }
        for (GameEvent.PotAwarded pot : potsThisHand) {
            System.out.println("  Pot #" + pot.potIndex() + " (" + pot.amount() + ") -> seats " + pot.winnerSeats());
        }
    }

    private int[] combine(int[] hole, int[] boardCards) {
        int[] combined = new int[hole.length + boardCards.length];
        System.arraycopy(hole, 0, combined, 0, hole.length);
        System.arraycopy(boardCards, 0, combined, hole.length, boardCards.length);
        return combined;
    }
}