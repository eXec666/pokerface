package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GameStateLoggingTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // Default behavior -- no logger attached
    // -------------------------------------------------------------------------

    @Test
    void defaultLogger_isNoOpAndNeverThrows() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        assertDoesNotThrow(() -> {
            state.startNewHand(1);
            state.postBlinds();
            state.setFirstActor();
            state.processAction(Action.CALL);
            state.processAction(Action.CHECK);
        });
    }

    @Test
    void setLogger_nullFallsBackToNoOp() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.setLogger(null);
        assertDoesNotThrow(state::postBlinds);
        assertSame(HandLogger.NO_OP, state.getLogger());
    }

    // -------------------------------------------------------------------------
    // HandStarted
    // -------------------------------------------------------------------------

    @Test
    void startNewHand_emitsHandStartedWithDealerAndSeed() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.startNewHand(42);

        GameEvent.HandStarted event = (GameEvent.HandStarted) logger.getEvents().get(0);
        assertEquals(state.getDealerIndex(), event.dealerSeat());
        assertEquals(42, event.handSeed());
    }

    // -------------------------------------------------------------------------
    // BlindPosted
    // -------------------------------------------------------------------------

    @Test
    void postBlinds_emitsSbAndBbEventsWithCorrectAmounts() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.postBlinds();

        List<GameEvent> events = logger.getEvents();
        assertEquals(2, events.size());

        GameEvent.BlindPosted sb = (GameEvent.BlindPosted) events.get(0);
        assertEquals("SB", sb.blindType());
        assertEquals(5, sb.amount());
        assertEquals(995, sb.resultingStack());

        GameEvent.BlindPosted bb = (GameEvent.BlindPosted) events.get(1);
        assertEquals("BB", bb.blindType());
        assertEquals(10, bb.amount());
        assertEquals(990, bb.resultingStack());
    }

    @Test
    void postBlinds_shortStackBlindLogsActualCappedAmount() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(0, "P0", 1000));
        players.add(new Player(1, "P1", 3)); // can't cover a 10-chip big blind
        GameState state = new GameState(players, 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.postBlinds();

        GameEvent.BlindPosted bb = (GameEvent.BlindPosted) logger.getEvents().get(1);
        assertEquals(3, bb.amount(), "logged amount must reflect the capped all-in blind, not the nominal big blind");
        assertEquals(0, bb.resultingStack());
    }

    // -------------------------------------------------------------------------
    // ActionTaken
    // -------------------------------------------------------------------------

    @Test
    void processAction_check_logsZeroAmount() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.processAction(Action.CHECK);

        GameEvent.ActionTaken event = (GameEvent.ActionTaken) logger.getEvents().get(0);
        assertEquals(0, event.seatIndex());
        assertEquals(Action.CHECK, event.action());
        assertEquals(0, event.amount());
        assertEquals(1000, event.resultingStack());
    }

    @Test
    void processAction_fold_logsZeroAmountAndCorrectAction() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.processAction(Action.FOLD);

        GameEvent.ActionTaken event = (GameEvent.ActionTaken) logger.getEvents().get(0);
        assertEquals(Action.FOLD, event.action());
        assertEquals(0, event.amount());
    }

    @Test
    void processAction_call_logsActualChipsCommittedNotNominalAmount() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.postBlinds(); // seat0 SB=5 (stack 995), seat1 BB=10 (stack 990), currentBet=10, seat0 to act
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.processAction(Action.CALL); // seat0 owes 5 more to match the BB

        GameEvent.ActionTaken event = (GameEvent.ActionTaken) logger.getEvents().get(0);
        assertEquals(Action.CALL, event.action());
        assertEquals(5, event.amount());
        assertEquals(990, event.resultingStack());
    }

    @Test
    void processAction_raise_logsDeltaCommittedNotRaiseToTarget() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.processAction(Action.RAISE, 150); // seat0 starts at roundBet 0, commits 150 total

        GameEvent.ActionTaken event = (GameEvent.ActionTaken) logger.getEvents().get(0);
        assertEquals(Action.RAISE, event.action());
        assertEquals(150, event.amount(), "logged amount is chips actually committed this action, not the raise-to target");
        assertEquals(850, event.resultingStack());
    }

    @Test
    void processAction_logsCorrectRound() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.advanceRound(); // FLOP
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.processAction(Action.CHECK);

        GameEvent.ActionTaken event = (GameEvent.ActionTaken) logger.getEvents().get(0);
        assertEquals(Round.FLOP, event.round());
    }

    // -------------------------------------------------------------------------
    // CommunityCardDealt
    // -------------------------------------------------------------------------

    @Test
    void dealCommunityCard_logsCardAndDealtCountExcludingBurns() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.dealCommunityCard(); // burn + flop1 -- only flop1 should be logged
        state.dealCommunityCard(); // flop2, no burn

        List<GameEvent> events = logger.getEvents();
        assertEquals(2, events.size(), "burns must not produce their own logged event");

        GameEvent.CommunityCardDealt first = (GameEvent.CommunityCardDealt) events.get(0);
        assertEquals(1, first.cardsDealtSoFar());
        GameEvent.CommunityCardDealt second = (GameEvent.CommunityCardDealt) events.get(1);
        assertEquals(2, second.cardsDealtSoFar());
    }

    // -------------------------------------------------------------------------
    // PotAwarded / HandEnded -- fold-win path
    // -------------------------------------------------------------------------

    @Test
    void checkFoldWin_logsAggregatePotAwardThenHandEnded() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.commitChips(players.get(0), 50);
        state.commitChips(players.get(1), 50);
        state.foldPlayer(players.get(1));

        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.checkFoldWin();

        List<GameEvent> events = logger.getEvents();
        assertEquals(2, events.size());

        GameEvent.PotAwarded potEvent = (GameEvent.PotAwarded) events.get(0);
        assertEquals(-1, potEvent.potIndex(), "fold-win path has no Pot objects, logged as an aggregate award");
        assertEquals(100, potEvent.amount());
        assertEquals(List.of(0), potEvent.winnerSeats());

        GameEvent.HandEnded endEvent = (GameEvent.HandEnded) events.get(1);
        assertTrue(endEvent.wonByFold());
        assertEquals(List.of(0), endEvent.winnerSeats());
    }

    // -------------------------------------------------------------------------
    // PotAwarded / HandEnded -- showdown path
    // -------------------------------------------------------------------------

    @Test
    void resolveShowdown_logsOnePotAwardedThenHandEnded_singlePot() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.dealHoleCards(0); // seed 0: seat0 holds the best hand (see GameStateTest)
        for (int i = 0; i < 5; i++) state.dealCommunityCard();
        for (Player p : players) state.commitChips(p, 100);

        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.resolveShowdown();

        List<GameEvent> events = logger.getEvents();
        long potEvents = events.stream().filter(e -> e instanceof GameEvent.PotAwarded).count();
        assertEquals(1, potEvents, "single-pot showdown should emit exactly one PotAwarded event");

        GameEvent.HandEnded endEvent = (GameEvent.HandEnded) events.get(events.size() - 1);
        assertFalse(endEvent.wonByFold());
        assertEquals(List.of(0), endEvent.winnerSeats());
    }

    @Test
    void resolveShowdown_sidePots_logOnePotAwardedEventPerPot() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        state.dealHoleCards(0);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        state.commitChips(players.get(0), 30);
        state.commitChips(players.get(1), 130);
        state.commitChips(players.get(2), 130);

        InMemoryHandLogger logger = new InMemoryHandLogger();
        state.setLogger(logger);

        state.resolveShowdown();

        long potEvents = logger.getEvents().stream().filter(e -> e instanceof GameEvent.PotAwarded).count();
        assertEquals(2, potEvents, "main pot + side pot must each log their own award");

        GameEvent last = logger.getEvents().get(logger.getEvents().size() - 1);
        assertInstanceOf(GameEvent.HandEnded.class, last, "HandEnded must be the terminal event, after every PotAwarded");
    }

    // -------------------------------------------------------------------------
    // InMemoryHandLogger
    // -------------------------------------------------------------------------

    @Test
    void inMemoryLogger_getEventsIsUnmodifiable() {
        InMemoryHandLogger logger = new InMemoryHandLogger();
        logger.log(new GameEvent.HandStarted(0, 1));
        assertThrows(UnsupportedOperationException.class,
                () -> logger.getEvents().add(new GameEvent.HandStarted(1, 2)));
    }

    @Test
    void inMemoryLogger_clearRemovesAllEvents() {
        InMemoryHandLogger logger = new InMemoryHandLogger();
        logger.log(new GameEvent.HandStarted(0, 1));
        logger.clear();
        assertTrue(logger.getEvents().isEmpty());
    }

    // -------------------------------------------------------------------------
    // FileHandLogger
    // -------------------------------------------------------------------------

    @Test
    void fileLogger_writesOneLinePerEvent(@TempDir Path tempDir) throws IOException {
        Path logPath = tempDir.resolve("test.log");
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);

        try (FileHandLogger logger = new FileHandLogger(logPath.toString())) {
            state.setLogger(logger);
            state.postBlinds();               // 2 BlindPosted events
            state.processAction(Action.CALL); // 1 ActionTaken event
        }

        List<String> lines = Files.readAllLines(logPath);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).startsWith("BLIND"));
        assertTrue(lines.get(1).startsWith("BLIND"));
        assertTrue(lines.get(2).startsWith("ACTION"));
    }

    @Test
    void fileLogger_defaultConstructorOverwritesExistingFile(@TempDir Path tempDir) throws IOException {
        Path logPath = tempDir.resolve("overwrite.log");
        Files.writeString(logPath, "stale content from a previous run\n");

        try (FileHandLogger logger = new FileHandLogger(logPath.toString())) {
            logger.log(new GameEvent.HandStarted(0, 1));
        }

        List<String> lines = Files.readAllLines(logPath);
        assertEquals(1, lines.size(), "constructing with append=false must discard prior content");
        assertFalse(lines.get(0).contains("stale content"));
    }

    @Test
    void fileLogger_appendModePreservesExistingContent(@TempDir Path tempDir) throws IOException {
        Path logPath = tempDir.resolve("append.log");
        Files.writeString(logPath, "session one\n");

        try (FileHandLogger logger = new FileHandLogger(logPath.toString(), true)) {
            logger.log(new GameEvent.HandStarted(0, 1));
        }

        List<String> lines = Files.readAllLines(logPath);
        assertEquals(2, lines.size());
        assertEquals("session one", lines.get(0));
    }
}