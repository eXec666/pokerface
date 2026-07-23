package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PlayerTest {

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void constructor_initializesExpectedDefaults() {
        Player p = new Player(2, "Alice", 1000);
        assertEquals(2, p.getSeatIndex());
        assertEquals("Alice", p.getName());
        assertEquals(1000, p.getStack());
        assertEquals(0, p.getRoundBet());
        assertEquals(0, p.getTotalCommitted());
        assertFalse(p.isFolded());
        assertFalse(p.isAllIn());
        assertArrayEquals(new int[]{-1, -1}, p.getHoleCards());
    }

    @Test
    void setName_updatesName() {
        Player p = new Player(0, "Bob", 500);
        p.setName("Robert");
        assertEquals("Robert", p.getName());
    }

    // -------------------------------------------------------------------------
    // Hole cards
    // -------------------------------------------------------------------------

    @Test
    void dealHoleCard_setsCorrectSlot() {
        Player p = new Player(0, "Alice", 1000);
        p.dealHoleCard(0, 5);
        p.dealHoleCard(1, 17);
        assertArrayEquals(new int[]{5, 17}, p.getHoleCards());
    }

    @Test
    void dealHoleCard_invalidSlotBelowRangeThrows() {
        Player p = new Player(0, "Alice", 1000);
        assertThrows(IllegalArgumentException.class, () -> p.dealHoleCard(-1, 5));
    }

    @Test
    void dealHoleCard_invalidSlotAboveRangeThrows() {
        Player p = new Player(0, "Alice", 1000);
        assertThrows(IllegalArgumentException.class, () -> p.dealHoleCard(2, 5));
    }

    @Test
    void getHoleCards_returnsDefensiveCopy() {
        Player p = new Player(0, "Alice", 1000);
        p.dealHoleCard(0, 5);
        p.dealHoleCard(1, 17);

        int[] firstRead = p.getHoleCards();
        firstRead[0] = 99; // mutate the returned array

        int[] secondRead = p.getHoleCards();
        assertEquals(5, secondRead[0], "Mutating a returned copy must not affect internal state");
    }

    // -------------------------------------------------------------------------
    // commit()
    // -------------------------------------------------------------------------

    @Test
    void commit_reducesStackAndIncreasesBets() {
        Player p = new Player(0, "Alice", 1000);
        p.commit(200);
        assertEquals(800, p.getStack());
        assertEquals(200, p.getRoundBet());
        assertEquals(200, p.getTotalCommitted());
    }

    @Test
    void commit_accumulatesTotalCommittedAcrossRounds() {
        Player p = new Player(0, "Alice", 1000);
        p.commit(100); // preflop
        p.resetForNewRound();
        p.commit(50);  // flop
        assertEquals(50, p.getRoundBet(), "roundBet should only reflect the current round");
        assertEquals(150, p.getTotalCommitted(), "totalCommitted should accumulate across rounds");
    }

    @Test
    void commit_exactStackTriggersAllIn() {
        Player p = new Player(0, "Alice", 300);
        p.commit(300);
        assertEquals(0, p.getStack());
        assertTrue(p.isAllIn());
    }

    @Test
    void commit_partialStackDoesNotTriggerAllIn() {
        Player p = new Player(0, "Alice", 300);
        p.commit(100);
        assertFalse(p.isAllIn());
    }

    @Test
    void commit_moreThanStackThrows() {
        Player p = new Player(0, "Alice", 100);
        assertThrows(IllegalArgumentException.class, () -> p.commit(150));
    }

    @Test
    void commit_negativeAmountThrows() {
        Player p = new Player(0, "Alice", 100);
        assertThrows(IllegalArgumentException.class, () -> p.commit(-10));
    }

    // -------------------------------------------------------------------------
    // fold() / isActive()
    // -------------------------------------------------------------------------

    @Test
    void fold_setsFoldedFlag() {
        Player p = new Player(0, "Alice", 100);
        p.fold();
        assertTrue(p.isFolded());
    }

    @Test
    void isActive_trueForFreshPlayer() {
        Player p = new Player(0, "Alice", 100);
        assertTrue(p.isActive());
    }

    @Test
    void isActive_falseWhenFolded() {
        Player p = new Player(0, "Alice", 100);
        p.fold();
        assertFalse(p.isActive());
    }

    @Test
    void isActive_falseWhenAllIn() {
        Player p = new Player(0, "Alice", 100);
        p.commit(100);
        assertFalse(p.isActive());
    }

    // -------------------------------------------------------------------------
    // resetForNewRound() / resetForNewHand()
    // -------------------------------------------------------------------------

    @Test
    void resetForNewRound_resetsOnlyRoundBet() {
        Player p = new Player(0, "Alice", 1000);
        p.commit(200);
        p.fold();
        p.resetForNewRound();
        assertEquals(0, p.getRoundBet());
        assertEquals(200, p.getTotalCommitted(), "totalCommitted must survive a round reset");
        assertTrue(p.isFolded(), "folded status must survive a round reset");
    }

    @Test
    void resetForNewHand_resetsAllHandState() {
        Player p = new Player(0, "Alice", 1000);
        p.dealHoleCard(0, 3);
        p.dealHoleCard(1, 9);
        p.commit(500);
        p.fold();

        p.resetForNewHand();

        assertEquals(0, p.getRoundBet());
        assertEquals(0, p.getTotalCommitted());
        assertFalse(p.isFolded());
        assertFalse(p.isAllIn());
        assertArrayEquals(new int[]{-1, -1}, p.getHoleCards());
        assertEquals(500, p.getStack(), "resetForNewHand must not touch the stack");
    }

    // -------------------------------------------------------------------------
    // addToStack() -- used when awarding pot winnings
    // -------------------------------------------------------------------------

    @Test
    void addToStack_increasesStack() {
        Player p = new Player(0, "Alice", 100);
        p.addToStack(250);
        assertEquals(350, p.getStack());
    }

    // -------------------------------------------------------------------------
    // setStack() -- direct override for ring-game-style stack resets
    // -------------------------------------------------------------------------

    @Test
    void setStack_overridesCurrentStackDirectly() {
        Player p = new Player(0, "Alice", 100);
        p.setStack(500);
        assertEquals(500, p.getStack());
    }

    @Test
    void setStack_bypassesCommitHistoryAndRoundState() {
        Player p = new Player(0, "Alice", 1000);
        p.commit(300);
        p.setStack(1000);
        assertEquals(1000, p.getStack(), "setStack must directly override the stack");
        assertEquals(300, p.getRoundBet(), "setStack must not touch unrelated betting state");
        assertEquals(300, p.getTotalCommitted());
    }

    @Test
    void setStack_zeroIsAllowed() {
        Player p = new Player(0, "Alice", 1000);
        assertDoesNotThrow(() -> p.setStack(0));
        assertEquals(0, p.getStack());
    }

    @Test
    void setStack_negativeThrows() {
        Player p = new Player(0, "Alice", 1000);
        assertThrows(IllegalArgumentException.class, () -> p.setStack(-1));
    }
}