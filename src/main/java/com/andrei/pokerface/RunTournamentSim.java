package com.andrei.pokerface;

import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class RunTournamentSim {
    public static void main(String[] args) {
        List<NamedAgent> bots = List.of(
                new NamedAgent("Random-A", new RandomAgent(1)),
                new NamedAgent("Random-B", new RandomAgent(2)),
                new NamedAgent("Caller", new AlwaysCallAgent()),
                new NamedAgent("AllIn", new AllInAgent())
        );

        IntSupplier seedSource = new Random(42)::nextInt;

        BotTournamentReport report = BotTournamentStatsCollector.collect(
                bots, 1000,
                BlindSchedule.increasing(new BlindLevel(10, 20), 0.33, 60_000, 25),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING.orAfter(500),
                seedSource, HandLogger.NO_OP, 1000);

        System.out.println(report.formatTable());
    }
}