package com.andrei.pokerface;

import java.util.Comparator;
import java.util.List;

/** Ring-game comparison across multiple named bots, with seat-position bias cancelled by rotation. */
public record BotPerformanceReport(List<BotStatLine> lines) {

    public BotPerformanceReport {
        lines = List.copyOf(lines);
    }

    public BotStatLine forBot(String name) {
        return lines.stream()
                .filter(l -> l.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No stats for bot: " + name));
    }

    /** Human-readable table, ranked best bb/100 to worst. */
    public String formatTable() {
        List<BotStatLine> ranked = lines.stream()
                .sorted(Comparator.comparingDouble(BotStatLine::bbPer100).reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s %10s %12s %12s%n", "Bot", "Hands", "bb/100", "+/- StdErr"));
        for (BotStatLine line : ranked) {
            sb.append(String.format("%-20s %10d %12.2f %12.2f%n",
                    line.name(), line.handsPlayed(), line.bbPer100(), line.bbPer100StdError()));
        }
        return sb.toString();
    }
}