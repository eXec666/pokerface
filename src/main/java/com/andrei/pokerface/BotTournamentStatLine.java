package com.andrei.pokerface;

/** One bot's aggregated tournament win rate, pooled across every seat it occupied. */
public record BotTournamentStatLine(String name, int wins, int tournamentsEntered, double winRate) {

    public static BotTournamentStatLine of(String name, int wins, int tournamentsEntered) {
        double rate = (tournamentsEntered == 0) ? 0.0 : (double) wins / tournamentsEntered;
        return new BotTournamentStatLine(name, wins, tournamentsEntered, rate);
    }
}