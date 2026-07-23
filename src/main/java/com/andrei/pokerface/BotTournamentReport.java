package com.andrei.pokerface;

import java.util.Comparator;
import java.util.List;

/** Tournament win-rate comparison across multiple named bots, with seat-position bias cancelled by rotation. */
public record BotTournamentReport(List<BotTournamentStatLine> lines, long inconclusiveCount) {

    public BotTournamentReport {
        lines = List.copyOf(lines);
    }

    public BotTournamentStatLine forBot(String name) {
        return lines.stream()
                .filter(l -> l.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No stats for bot: " + name));
    }

    public String formatTable() {
        List<BotTournamentStatLine> ranked = lines.stream()
                .sorted(Comparator.comparingDouble(BotTournamentStatLine::winRate).reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %10s %10s %12s%n", "Bot", "Wins", "Entered", "Win Rate"));
        for (BotTournamentStatLine line : ranked) {
            sb.append(String.format("%-20s %10d %10d %11.1f%%%n",
                    line.name(), line.wins(), line.tournamentsEntered(), line.winRate() * 100));
        }
        sb.append("Inconclusive tournaments (hand cap hit): ").append(inconclusiveCount).append('\n');
        return sb.toString();
    }
}