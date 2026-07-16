package com.andrei.pokerface;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameStateBurnTest {

    private List<Player> makePlayers(int count, int startingStack) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new Player(i, "P" + i, startingStack));
        }
        return players;
    }

    @Test
    void dealCommunityCard_burnsOneCardBeforeFirstFlopCard() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1); // 4 cards dealt -> 48 remaining
        assertEquals(48, state.buildPlayerView(0).deckRemaining());

        state.dealCommunityCard(); // burn + 1st flop card = 2 consumed

        assertEquals(46, state.buildPlayerView(0).deckRemaining());
    }

    @Test
    void dealCommunityCard_doesNotBurnForSecondOrThirdFlopCard() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1); // 48 remaining
        state.dealCommunityCard(); // burn + flop1 -> 46
        state.dealCommunityCard(); // flop2, no burn -> 45
        state.dealCommunityCard(); // flop3, no burn -> 44

        assertEquals(44, state.buildPlayerView(0).deckRemaining());
    }

    @Test
    void dealCommunityCard_burnsBeforeTurn() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        for (int i = 0; i < 3; i++) state.dealCommunityCard(); // flop, 44 remaining

        state.dealCommunityCard(); // burn + turn = 2 consumed

        assertEquals(42, state.buildPlayerView(0).deckRemaining());
    }

    @Test
    void dealCommunityCard_burnsBeforeRiver() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        for (int i = 0; i < 4; i++) state.dealCommunityCard(); // flop + turn, 42 remaining

        state.dealCommunityCard(); // burn + river = 2 consumed

        assertEquals(40, state.buildPlayerView(0).deckRemaining());
    }

    @Test
    void dealCommunityCard_totalHandConsumesExpectedCardCount() {
        // 2 players * 2 hole cards = 4, + 3 burns + 5 community = 12 consumed of 52.
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        assertEquals(40, state.buildPlayerView(0).deckRemaining());
    }

    @Test
    void dealCommunityCard_stillThrowsPastFiveCardsWithBurnsAccountedFor() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        assertThrows(IllegalStateException.class, state::dealCommunityCard);
    }

    @Test
    void dealCommunityCard_communityCardsStillCorrectLengthPerStreet() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        state.dealCommunityCard();
        state.dealCommunityCard();
        state.dealCommunityCard();
        assertEquals(3, state.getCommunityCards().length, "burns must not appear in getCommunityCards()");

        state.dealCommunityCard();
        assertEquals(4, state.getCommunityCards().length);

        state.dealCommunityCard();
        assertEquals(5, state.getCommunityCards().length);
    }

    @Test
    void burnedCardsAreNeverDealtToPlayersOrBoard() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        for (int i = 0; i < 5; i++) state.dealCommunityCard();

        Set<Integer> seen = new HashSet<>();
        for (Player p : state.getPlayers()) {
            for (int c : p.getHoleCards()) {
                assertTrue(seen.add(c), "no card should be dealt twice, including a re-surfaced burn card");
            }
        }
        for (int c : state.getCommunityCards()) {
            assertTrue(seen.add(c), "no card should be dealt twice, including a re-surfaced burn card");
        }

        // 4 hole cards + 5 community cards = 9 unique cards actually seen.
        assertEquals(9, seen.size());
        // 9 dealt + 3 burned = 12 consumed of 52.
        assertEquals(52 - 9 - 3, state.buildPlayerView(0).deckRemaining());
    }

    @Test
    void startNewHand_freshDeckAccountsForBurnsOnSecondHandToo() {
        GameState state = new GameState(makePlayers(2, 1000), 5, 10);
        state.dealHoleCards(1);
        for (int i = 0; i < 5; i++) state.dealCommunityCard(); // 40 remaining, first hand

        state.startNewHand(2); // resets and reshuffles a full 52-card deck, deals hole cards

        assertEquals(48, state.buildPlayerView(0).deckRemaining(), "second hand starts from a full deck again");

        for (int i = 0; i < 5; i++) state.dealCommunityCard();
        assertEquals(40, state.buildPlayerView(0).deckRemaining(), "burns apply identically on every hand");
    }
}