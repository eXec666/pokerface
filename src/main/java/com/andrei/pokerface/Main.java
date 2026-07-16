package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;

public class Main {
    public static void main(String[] args) {
        int playerCount = 6;
        int startingStack = 1000;

        List<Player> players = new ArrayList<>();
        List<PokerAgent> agents = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            players.add(new Player(i, "Bot" + i, startingStack));
            agents.add(new RandomAgent(i * 97L + 1));
        }

        IntSupplier seedSource = new Random(42)::nextInt;

        SessionResult result = SessionRunner.runSession(
                players, agents,
                BlindSchedule.constant(10, 20),
                BustHandler.ELIMINATE,
                SessionEndCondition.LAST_PLAYER_STANDING.orAfter(5000),
                seedSource,
                new FileHandLogger("game.log"));

        System.out.println("Hands played: " + result.handsPlayed());
        result.winner().ifPresentOrElse(
                w -> System.out.println("Winner: " + w.getName() + " with " + w.getStack() + " chips"),
                () -> System.out.println("No single winner within hand cap"));
    }
}