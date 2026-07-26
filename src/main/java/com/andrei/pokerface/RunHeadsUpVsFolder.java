package com.andrei.pokerface;

import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class RunHeadsUpVsFolder {
    public static void main(String[] args) {
        List<NamedAgent> bots = List.of(
                new NamedAgent("MonteCarlo", new MonteCarloCallFoldAgent(300, 7)),
                new NamedAgent("Folder", new FoldingAgent())
        );

        IntSupplier dealSeedSource = new Random(42)::nextInt;

        BotPerformanceReport report = BotRingStatsCollector.collect(
                bots, 5, 10, 1000, 20_000, 100, dealSeedSource, 12345L);

        System.out.println(report.formatTable());
    }
}