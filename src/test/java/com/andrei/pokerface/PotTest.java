package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PotTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    // -------------------------------------------------------------------------
    // Pot class basics
    // -------------------------------------------------------------------------

    @Test
    void constructor_setsAmountAndEligibleSeats() {
        Set<Integer> eligible = new HashSet<>(List.of(0, 1));
        Pot pot = new Pot(300, eligible);
        assertEquals(300, pot.getAmount());
        assertEquals(eligible, pot.getEligibleSeats());
    }

    @Test
    void addAmount_increasesAmount() {
        Pot pot = new Pot(100, new HashSet<>(List.of(0)));
        pot.addAmount(50);
        assertEquals(150, pot.getAmount());
    }

    @Test
    void addAmount_negativeThrows() {
        Pot pot = new Pot(100, new HashSet<>(List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> pot.addAmount(-10));
    }

    @Test
    void getEligibleSeats_isUnmodifiable() {
        Pot pot = new Pot(100, new HashSet<>(List.of(0, 1)));
        Set<Integer> seats = pot.getEligibleSeats();
        assertThrows(UnsupportedOperationException.class, () -> seats.add(5));
    }

    @Test
    void isEligible_reflectsMembership() {
        Pot pot = new Pot(100, new HashSet<>(List.of(0, 2)));
        assertTrue(pot.isEligible(0));
        assertTrue(pot.isEligible(2));
        assertFalse(pot.isEligible(1));
    }

    // -------------------------------------------------------------------------
    // Side-pot / all-in computation (GameState.computeSidePots())
    // -------------------------------------------------------------------------

    private Pot potContaining(List<Pot> pots, int seatIndex) {
        return pots.stream().filter(p -> p.isEligible(seatIndex)).findFirst().orElse(null);
    }

    @Test
    void computeSidePots_noAllIn_producesSinglePotEligibleToEveryone() {
        GameState state = new GameState(makePlayers(3, 1000), 5, 10);
        List<Player> players = state.getPlayers();
        for (Player p : players) {
            state.commitChips(p, 100);
        }

        List<Pot> pots = state.computeSidePots();

        assertEquals(1, pots.size());
        assertEquals(300, pots.get(0).getAmount());
        for (Player p : players) {
            assertTrue(pots.get(0).isEligible(p.getSeatIndex()));
        }
    }

    @Test
    void computeSidePots_oneShortStackAllIn_producesMainPotAndSidePot() {
        // A all-in for 50, B and C both commit 200, nobody folds.
        List<Player> players = makePlayers(3, 1000);
        GameState state = new GameState(players, 5, 10);
        Player a = players.get(0), b = players.get(1), c = players.get(2);

        a.commit(50);
        b.commit(200);
        c.commit(200);

        List<Pot> pots = state.computeSidePots();

        assertEquals(2, pots.size(), "expected a main pot plus one side pot");

        Pot mainPot = potContaining(pots, a.getSeatIndex());
        assertEquals(150, mainPot.getAmount()); // 50 * 3 contributors
        assertTrue(mainPot.isEligible(a.getSeatIndex()));
        assertTrue(mainPot.isEligible(b.getSeatIndex()));
        assertTrue(mainPot.isEligible(c.getSeatIndex()));

        Pot sidePot = pots.stream().filter(p -> p != mainPot).findFirst().orElseThrow();
        assertEquals(300, sidePot.getAmount()); // (200-50) * 2 contributors
        assertFalse(sidePot.isEligible(a.getSeatIndex()), "short stack is not eligible for the side pot");
        assertTrue(sidePot.isEligible(b.getSeatIndex()));
        assertTrue(sidePot.isEligible(c.getSeatIndex()));

        assertEquals(450, state.totalPot());
    }

    @Test
    void computeSidePots_threeDistinctAllInLevels_producesThreePots() {
        // A all-in for 30, B all-in for 80, C commits 150, nobody folds.
        List<Player> players = makePlayers(3, 1000);
        GameState state = new GameState(players, 5, 10);
        Player a = players.get(0), b = players.get(1), c = players.get(2);

        a.commit(30);
        b.commit(80);
        c.commit(150);

        List<Pot> pots = state.computeSidePots();
        assertEquals(3, pots.size());

        Pot layer1 = potContaining(pots, a.getSeatIndex());
        assertEquals(90, layer1.getAmount()); // 30 * 3
        assertTrue(layer1.isEligible(b.getSeatIndex()));
        assertTrue(layer1.isEligible(c.getSeatIndex()));

        Pot layer2 = pots.stream()
                .filter(p -> p.isEligible(b.getSeatIndex()) && !p.isEligible(a.getSeatIndex()))
                .findFirst().orElseThrow();
        assertEquals(100, layer2.getAmount()); // (80-30) * 2

        Pot layer3 = pots.stream()
                .filter(p -> !p.isEligible(a.getSeatIndex()) && !p.isEligible(b.getSeatIndex()))
                .findFirst().orElseThrow();
        assertEquals(70, layer3.getAmount()); // (150-80) * 1
        assertTrue(layer3.isEligible(c.getSeatIndex()));

        assertEquals(260, state.totalPot()); // 30 + 80 + 150
    }

    @Test
    void computeSidePots_foldedContributorStillCountsTowardPotAmountButNotEligibility() {
        // A commits 50 then folds, B and C both commit 100.
        List<Player> players = makePlayers(3, 1000);
        GameState state = new GameState(players, 5, 10);
        Player a = players.get(0), b = players.get(1), c = players.get(2);

        a.commit(50);
        a.fold();
        b.commit(100);
        c.commit(100);

        List<Pot> pots = state.computeSidePots();

        // Both layers end up with the same eligible set {B, C}, so they merge into one pot.
        assertEquals(1, pots.size());
        Pot pot = pots.get(0);
        assertEquals(250, pot.getAmount(), "folded player's chips still count toward the pot");
        assertFalse(pot.isEligible(a.getSeatIndex()), "folded player cannot win the pot");
        assertTrue(pot.isEligible(b.getSeatIndex()));
        assertTrue(pot.isEligible(c.getSeatIndex()));
    }

    @Test
    void computeSidePots_playerWithNoCommitmentIsIgnored() {
        // C never commits any chips this hand (e.g. folded before posting anything).
        List<Player> players = makePlayers(3, 1000);
        GameState state = new GameState(players, 5, 10);
        Player a = players.get(0), b = players.get(1), c = players.get(2);

        a.commit(50);
        b.commit(50);
        c.fold(); // never committed

        List<Pot> pots = state.computeSidePots();

        assertEquals(1, pots.size());
        assertEquals(100, pots.get(0).getAmount());
        assertFalse(pots.get(0).isEligible(c.getSeatIndex()));
    }

    @Test
    void computeSidePots_emptyWhenNobodyHasCommittedAnything() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        List<Pot> pots = state.computeSidePots();
        assertTrue(pots.isEmpty());
        assertEquals(0, state.totalPot());
    }
}