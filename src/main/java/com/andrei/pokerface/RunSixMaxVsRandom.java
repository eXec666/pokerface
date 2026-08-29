package com.andrei.pokerface;

import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class RunSixMaxVsRandom {
    public static void main(String[] args) {
        List<NamedAgent> bots = List.of(
                new NamedAgent("MonteCarlo", new MonteCarloCallFoldAgent(300, 7)),
                new NamedAgent("Random-1", new RandomAgent(1)),
                new NamedAgent("Random-2", new RandomAgent(2)),
                new NamedAgent("Random-3", new RandomAgent(3)),
                new NamedAgent("Random-4", new RandomAgent(4)),
                new NamedAgent("Random-5", new RandomAgent(5))
        );

        IntSupplier dealSeedSource = new Random(42)::nextInt;

        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 1000, 20_000, 100, dealSeedSource, 12345L);

        System.out.println(report.formatTable());
    }
}