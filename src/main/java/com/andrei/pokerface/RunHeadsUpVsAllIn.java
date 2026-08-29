package com.andrei.pokerface;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class RunHeadsUpVsAllIn {
    public static void main(String[] args) {
        List<Player> players = List.of(new Player(0, "MonteCarlo", 1000), new Player(1, "AllIn", 1000));
        List<PokerAgent> agents = List.of(new MonteCarloCallFoldAgent(300, 7), new AllInAgent());

        AtomicInteger counter = new AtomicInteger(0);
        IntSupplier dealSeedSource = counter::getAndIncrement;

        InMemoryHandLogger logger = new InMemoryHandLogger();
        RingGameResult result = RingGameRunner.runBatch(
                players, agents, 5, 10, 1000, 20_000, dealSeedSource, logger);

        System.out.println("bb/100 seat 0 (MonteCarlo, pinned): " + result.bbPer100(0)
                + " +/- " + result.bbPer100StdError(0));

        long calls = logger.getEvents().stream()
                .filter(e -> e instanceof GameEvent.ActionTaken a
                        && a.seatIndex() == 0 && a.action() == Action.CALL)
                .count();
        long folds = logger.getEvents().stream()
                .filter(e -> e instanceof GameEvent.ActionTaken a
                        && a.seatIndex() == 0 && a.action() == Action.FOLD)
                .count();

        System.out.println("MonteCarlo (seat 0, pinned) -- calls=" + calls + " folds=" + folds
                + " callRate=" + (double) calls / (calls + folds));
    }
}