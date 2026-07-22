package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConsoleHandLoggerTest {

    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream captured;

    private void captureStdout() {
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    @Test
    void handStarted_printsDealerSeatAndResetsBoardAndPots() {
        captureStdout();
        ConsoleHandLogger logger = new ConsoleHandLogger(makePlayers(2, 1000));

        logger.log(new GameEvent.HandStarted(3, 99));

        assertTrue(output().contains("dealer seat 3"));
    }

    @Test
    void wonByFold_printsSummaryWithoutCardsAndTotalsPots() {
        captureStdout();
        List<Player> players = makePlayers(2, 1000);
        ConsoleHandLogger logger = new ConsoleHandLogger(players);

        logger.log(new GameEvent.HandStarted(0, 1));
        logger.log(new GameEvent.PotAwarded(-1, 100, List.of(0)));
        logger.log(new GameEvent.HandEnded(true, List.of(0)));

        String out = output();
        assertTrue(out.contains("Hand won by fold"));
        assertTrue(out.contains("100 chips"));
        assertFalse(out.contains("Showdown"));
    }

    @Test
    void showdown_printsHoleCardsAndDescriptionForNonFoldedPlayers() {
        captureStdout();
        List<Player> players = makePlayers(2, 1000);
        players.get(0).dealHoleCard(0, 0);  // Ac
        players.get(0).dealHoleCard(1, 1);  // Ad
        players.get(1).dealHoleCard(0, 48); // Kc
        players.get(1).dealHoleCard(1, 49); // Kd
        ConsoleHandLogger logger = new ConsoleHandLogger(players);

        logger.log(new GameEvent.HandStarted(0, 1));
        // 3d, 4h, 5s, 7d, 8h -- mixed suits, no straight, no flush;
        // best hand for both seats stays their own pocket pair.
        for (int card : new int[]{9, 14, 19, 25, 30}) {
            logger.log(new GameEvent.CommunityCardDealt(Round.RIVER, card, 1));
        }
        logger.log(new GameEvent.PotAwarded(0, 200, List.of(0)));
        logger.log(new GameEvent.HandEnded(false, List.of(0)));

        String out = output();
        assertTrue(out.contains("Showdown"));
        assertTrue(out.contains("Seat 0"));
        assertTrue(out.contains("Ac, Ad"));
        assertTrue(out.contains("Pair of Aces"));
        assertTrue(out.contains("Seat 1"));
        assertTrue(out.contains("Kc, Kd"));
        assertTrue(out.contains("Pair of Kings"));
        assertTrue(out.contains("Pot #0 (200)"));
    }

    @Test
    void showdown_excludesFoldedPlayers() {
        captureStdout();
        List<Player> players = makePlayers(2, 1000);
        players.get(0).dealHoleCard(0, 0);
        players.get(0).dealHoleCard(1, 1);
        players.get(1).dealHoleCard(0, 48);
        players.get(1).dealHoleCard(1, 49);
        players.get(1).fold();
        ConsoleHandLogger logger = new ConsoleHandLogger(players);

        logger.log(new GameEvent.HandStarted(0, 1));
        // Full 5-card board, matching what resolveShowdown always guarantees
        // in real play -- describeBestHand is never called on fewer than 7 cards.
        for (int card : new int[]{9, 14, 19, 25, 30}) {
            logger.log(new GameEvent.CommunityCardDealt(Round.RIVER, card, 1));
        }
        logger.log(new GameEvent.HandEnded(false, List.of(0)));

        String out = output();
        assertTrue(out.contains("Seat 0"));
        assertFalse(out.contains("Seat 1"));
    }

    @Test
    void showdown_excludesPlayersNeverDealtIn() {
        captureStdout();
        List<Player> players = makePlayers(2, 1000); // hole cards remain -1,-1
        ConsoleHandLogger logger = new ConsoleHandLogger(players);

        logger.log(new GameEvent.HandStarted(0, 1));
        logger.log(new GameEvent.HandEnded(false, List.of()));

        assertFalse(output().contains("Seat"));
    }

    @Test
    void boardResetsBetweenHands() {
        captureStdout();
        List<Player> players = makePlayers(2, 1000);
        players.get(0).dealHoleCard(0, 0);
        players.get(0).dealHoleCard(1, 4);
        ConsoleHandLogger logger = new ConsoleHandLogger(players);

        logger.log(new GameEvent.HandStarted(0, 1));
        logger.log(new GameEvent.CommunityCardDealt(Round.FLOP, 48, 1));
        logger.log(new GameEvent.HandStarted(0, 2)); // new hand -- board must reset
        logger.log(new GameEvent.HandEnded(false, List.of()));

        // With no board cards accumulated for the second hand, describeBestHand
        // runs on just the 2 hole cards -- shouldn't throw despite < 5 cards.
        assertDoesNotThrow(() -> logger.log(new GameEvent.HandEnded(false, List.of())));
    }
}