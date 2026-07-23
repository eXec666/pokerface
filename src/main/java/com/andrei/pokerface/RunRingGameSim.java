package com.andrei.pokerface;

import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class RunRingGameSim {
    public static void main(String[] args) {
        long seatShuffleSeed = 2024L;

        List<NamedAgentFactory> bots = List.of(
                new NamedAgentFactory("Random-A", RandomAgent::new),
                new NamedAgentFactory("Random-B", RandomAgent::new),
                new NamedAgentFactory("Caller", seed -> new AlwaysCallAgent()),
                new NamedAgentFactory("Folder", seed -> new FoldingAgent())
        );

        IntSupplier dealSeedSource = new Random(42)::nextInt;
        IntSupplier agentSeedSource = new Random(1337)::nextInt;

        BotPerformanceReport report = BotRingStatsCollector.collectWithSeedRotation(
            bots, 10, 20, 2000, 200_000, 100, dealSeedSource, agentSeedSource, seatShuffleSeed);
            


        System.out.println(report.formatTable());
    }
}