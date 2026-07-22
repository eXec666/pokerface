package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

/**
 * Entry point for a human to play a short freezeout against RandomAgent bots
 * via HumanCliAgent. Separate from Main (which runs a pure bot-vs-bot sim) --
 * this wires stdin/stdout into the session and prints hand-by-hand results,
 * neither of which the bot-only entry point needs.
 */
public class PlayHuman {
    public static void main(String[] args) {
        int botCount = 6;
        int startingStack = 1000;

        List<Player> players = new ArrayList<>();
        List<PokerAgent> agents = new ArrayList<>();

        players.add(new Player(0, "You", startingStack));
        agents.add(new HumanCliAgent(System.in, System.out));

        for (int i = 1; i <= botCount; i++) {
            players.add(new Player(i, "Bot" + i, startingStack));
            agents.add(new RandomAgent(i * 97L + 1));
        }

        IntSupplier seedSource = new Random()::nextInt;
        BlindSchedule schedule = BlindSchedule.increasing(
                new BlindLevel(10, 20), 0.5, 60_000, 5); // +50% every simulated minute

        ConsoleHandLogger consoleLogger = new ConsoleHandLogger(players);
        FileHandLogger fileLogger = new FileHandLogger("game.log");
        HandLogger logger = new CompositeHandLogger(consoleLogger, fileLogger);
        System.out.println("Seated players:");
        for (Player p : players) {
            System.out.println("  seat " + p.getSeatIndex() + ": " + p.getName() + " (" + p.getStack() + " chips)");
        }
        SessionResult result;
        try {
            result = SessionRunner.runSession(
                    players, agents,
                    schedule,
                    BustHandler.ELIMINATE,
                    SessionEndCondition.LAST_PLAYER_STANDING.orAfter(40),
                    seedSource,
                    logger);
        } finally {
            fileLogger.close();
        }
        System.out.println("\n===== Session over =====");
        System.out.println("Hands played: " + result.handsPlayed());
        result.winner().ifPresentOrElse(
                w -> System.out.println("Winner: " + w.getName() + " with " + w.getStack() + " chips"),
                () -> System.out.println("No single winner within the hand cap. Final stacks:"));
        if (result.winner().isEmpty()) {
            for (Player p : result.finalPlayers()) {
                System.out.println("  " + p.getName() + ": " + p.getStack()
                        + (p.isEliminated() ? " (eliminated)" : ""));
            }
        }
    }
}