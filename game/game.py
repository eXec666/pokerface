"""#TODO: Module Description"""
from player import Player, ActionType
from typing import Optional
from game_state import GameState

class IllegalActionError(Exception):
    def __init__(self, message: str) -> None:
        self.message = message
        super().__init__(self.message)

class Game:
    """
    The game engine - Manages a poker session across multiple hands.

    Instance Attributes:
        - players: all players in the session
        - dealer: index of the current dealer
        - small_blind: small blind amount
        - big_blind: big blind amount
        - state: The game's current GameState, None between hands
    """
    players: list[Player]
    dealer: int
    small_blind: float
    big_blind: float
    state: Optional[GameState]

    def __init__(self, players: list[Player], small_blind: float, big_blind: float) -> None:
        self.players = players
        self.dealer = 0
        self.small_blind = small_blind
        self.big_blind = big_blind
        self.state = None

    def run(self) -> None:
        """
        Run the session until one player remains
        """
        while len(self._active_players()) > 1:
            self._play_hand()
            self.dealer = (self.dealer + 1) % len(self.players)

    def _play_hand(self) -> None:
        """Play a full hand in the current game."""
        # Init the game state
        self.state = GameState(self.players, self.dealer, self.small_blind, self.big_blind)
        self._post_blinds()
        self._deal_hole_cards()
        self._betting_round()
        self._deal_community_cards(3) # Flop
        self._betting_round()
        self._deal_community_cards(1) # Turn
        self._betting_round()
        self._deal_community_cards(1) # River
        self._betting_round()
        self._showdown()

    def _betting_round(self) -> None:
        """
        Run a round of bets.
        Each player, starting from curr_actor, is queried for an action, which is one of:
            - FOLD
            - CHECK
            - CALL
            - RAISE
        The loop goes through each player, takes in their action, verifies it, and mutates GameState to reflect
        the bets that were made to future players.
        The loop stops when a whole cycle of bets has been completed with no player raising.
        """
        # track the last player who raised.
        last_aggressor = self.state.active_players[self.state.curr_actor]
        # boolean folded flag:
        folded = False

        while True:
            actor_idx = self.state.active_players[self.state.curr_actor]
            actor = self.state.players[actor_idx]
            total_bet = self.state.player_bets[actor_idx]
            action = actor.get_action(self.state)

            # Folding
            if action.action_type == ActionType.FOLD:
                # remove the actor from active players & advance
                self.state.active_players.pop(self.state.curr_actor)
                folded = True

            # Checking
            elif action.action_type == ActionType.CHECK:
                if self.state.current_bet > 0:
                    raise IllegalActionError("Cannot check when there is a bet to call")
                # Literally do nothing, just advance

            # Calling
            elif action.action_type == ActionType.CALL:
                if total_bet + action.amount < self.state.current_bet:
                    raise IllegalActionError(f"Must put in at least {self.state.current_bet - total_bet} to call")
                if action.amount > actor.balance:
                    raise IllegalActionError(f"Cannot bet more than your current balance")
                # Subtract action.amount from balance, update player_bets, and advance
                actor.balance -= action.amount
                self.state.player_bets[actor_idx] += action.amount

            # Raising
            elif action.action_type == ActionType.RAISE:
                increment = (total_bet + action.amount) - self.state.current_bet
                if increment < self.state.last_raise_amount:
                    raise IllegalActionError(f"Minimum raise increment is {self.state.last_raise_amount}")
                if action.amount > actor.balance:
                    raise IllegalActionError(f"Cannot bet more than your current balance")
                # update increment, current_bet, last_aggressor, player_bets, and player balance
                self.state.last_raise_amount = increment
                self.state.current_bet = total_bet + action.amount
                last_aggressor = actor_idx
                actor.balance -= action.amount
                self.state.player_bets[actor_idx] += action.amount

            # Advance to the next player
            if not folded:
                self.state.curr_actor = (self.state.curr_actor + 1) % len(self.state.active_players)
            else:
                # if a player has folded, we do not advance the counter, only wrap around if needed.
                self.state.curr_actor %= len(self.state.active_players)

            # Stop when we've come back around to the last aggressor
            if self.state.active_players[self.state.curr_actor] == last_aggressor:
                break

    def _deal_hole_cards(self) -> None:
        """
        Deal cards to all players.
        Every player receives two cards from the deck.
        Cards are distributed in playing order, starting from index = dealer + 1.
        Cards are given out one at a time, i.e the process takes two cycles.
        """
        start = (self.dealer + 1) % len(self.state.players)
        order = list(range(start, len(self.state.players))) + list(range(0, start))

        # Deal first cards
        first_cards = {}
        for i in order:
            first_cards[i] = self.state.deck.draw()

        # Deal second cards and assign hands
        for i in order:
            self.state.players[i].hand = (first_cards[i], self.state.deck.draw())

        return

    def _deal_community_cards(self, n: int) -> None:
        """
        Deal the cards onto the table for each street:
        Draw and discard the burn card.
        Draw a further n cards and add them to the table
        """
        # Discard the burn card
        self.state.deck.draw()

        for i in range(n):
            card = self.state.deck.draw()
            self.state.table.append(card)

    def _post_blinds(self) -> None:
        """
        Mutate GameState to process the small and big blind logic.

        NOTE: This does not currently account for side pots and all-ins at the blind.
        This will be added later. For now the blind is simply capped by the person's balance.
        Other players are still required to pay at least big_blind to enter.
        """

        # Compute player indices for the blind holders
        small_blind_idx = (self.dealer + 1) % len(self.players)
        big_blind_idx = (self.dealer + 2) % len(self.players)

        # Deduct corresponding amount from players' balances
        small_blind_amount = min(self.state.small_blind, self.state.players[small_blind_idx].balance)
        big_blind_amount = min(self.state.big_blind, self.state.players[big_blind_idx].balance)

        self.state.players[small_blind_idx].balance -= small_blind_amount
        self.state.players[big_blind_idx].balance -= big_blind_amount

        # Append both bets to the pot
        self.state.pot += (self.state.small_blind + self.state.big_blind)

        # Update state.player_bets
        self.state.player_bets[small_blind_idx] = self.state.small_blind
        self.state.player_bets[big_blind_idx] = self.state.big_blind

        # Set the current bet to big_blind
        self.state.current_bet = self.state.big_blind


    def _showdown(self) -> None:
        pass

    def _active_players(self) -> list[Player]:
        return [p for p in self.players if p.balance > 0]