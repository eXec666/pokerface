package com.andrei.pokerface;

import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class RunHeadsUpVsRandom {
    public static void main(String[] args) {
        List<NamedAgent> bots = List.of(
                new NamedAgent("MonteCarlo", new MonteCarloCallFoldAgent(300, 7)),
                new NamedAgent("Random", new RandomAgent(99))
        );

        IntSupplier dealSeedSource = new Random(42)::nextInt;

        InMemoryHandLogger logger = new InMemoryHandLogger();
        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 1000, 20_000, 100, dealSeedSource, 12345L, logger);

        System.out.println(report.formatTable());

        // Diagnostic: how large are RandomAgent's raises relative to the blinds?
        java.util.DoubleSummaryStatistics raiseStats = logger.getEvents().stream()
                .filter(e -> e instanceof GameEvent.ActionTaken a && a.action() == Action.RAISE)
                .mapToDouble(e -> ((GameEvent.ActionTaken) e).amount() / 10.0) // amount in big blinds
                .summaryStatistics();

        System.out.println("Raise sizes in bb -- count=" + raiseStats.getCount()
                + " avg=" + raiseStats.getAverage()
                + " max=" + raiseStats.getMax());
    }
}