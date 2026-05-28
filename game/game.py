"""#TODO: Module Description"""
from player import Player, ActionType
from typing import Optional
from game.game_state import GameState, Pot
from game.evaluator import best_hand, compare_hands

class IllegalActionError(Exception):
    """
    An Error indicating an action that is illegal according to the rules of the game.
    """
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

    Representation Invariants:
        -
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
        """
        #TODO: Docstring
        """
        for player in self.players:
            player.hand = None
        self.state = GameState(self.players, self.dealer, self.small_blind, self.big_blind)
        self._post_blinds()
        self._deal_hole_cards()
        self._betting_round()
        if len(self.state.active_players) <= 1:
            self._showdown()
            return
        self._deal_community_cards(3)
        self._betting_round()
        if len(self.state.active_players) <= 1:
            self._showdown()
            return
        self._deal_community_cards(1)
        self._betting_round()
        if len(self.state.active_players) <= 1:
            self._showdown()
            return
        self._deal_community_cards(1)
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
        # Early return if only one player remains
        if len(self.state.active_players) <= 1:
            return

        # wrap curr_actor in case active_players shrank since last round
        self.state.curr_actor %= len(self.state.active_players)

        # track the last player who raised
        last_aggressor = self.state.active_players[self.state.curr_actor]
        folded = False

        while True:
            folded = False
            actor_idx = self.state.active_players[self.state.curr_actor]
            actor = self.state.players[actor_idx]
            total_bet = self.state.player_bets[actor_idx]
            action = actor.get_action(self.state)

            # Folding
            if action.action_type == ActionType.FOLD:
                self.state.active_players.pop(self.state.curr_actor)
                folded = True
                if len(self.state.active_players) <= 1:
                    break

            # Checking
            elif action.action_type == ActionType.CHECK:
                if self.state.current_bet > 0:
                    raise IllegalActionError("Cannot check when there is a bet to call")

            # Calling
            elif action.action_type == ActionType.CALL:
                still_needed = self.state.current_bet - total_bet
                if action.amount < still_needed and action.amount < actor.balance:
                    raise IllegalActionError(f"Must put in at least {still_needed} to call")
                if action.amount > actor.balance:
                    raise IllegalActionError("Cannot bet more than your current balance")
                actor.balance -= action.amount
                self.state.player_bets[actor_idx] += action.amount
                self.state.pots[-1].amount += action.amount
                if actor.balance == 0:
                    self._handle_all_in(actor_idx, self.state.player_bets[actor_idx])
                    folded = True  # treat as removed from active players

            # Raising
            elif action.action_type == ActionType.RAISE:
                increment = (total_bet + action.amount) - self.state.current_bet
                if increment < self.state.last_raise_amount:
                    raise IllegalActionError(f"Minimum raise increment is {self.state.last_raise_amount}")
                if action.amount > actor.balance:
                    raise IllegalActionError("Cannot bet more than your current balance")
                self.state.last_raise_amount = increment
                self.state.current_bet = total_bet + action.amount
                last_aggressor = actor_idx
                actor.balance -= action.amount
                self.state.player_bets[actor_idx] += action.amount
                self.state.pots[-1].amount += action.amount
                if actor.balance == 0:
                    self._handle_all_in(actor_idx, self.state.player_bets[actor_idx])
                    folded = True  # treat as removed from active players

            # Advance to the next player
            if not folded:
                self.state.curr_actor = (self.state.curr_actor + 1) % len(self.state.active_players)
            else:
                self.state.curr_actor %= len(self.state.active_players)

            if len(self.state.active_players) <= 1:
                break

            # update last_aggressor if they were removed from active players
            if last_aggressor not in self.state.active_players:
                last_aggressor = self.state.active_players[self.state.curr_actor]

            # stop when we've come back around to the last aggressor
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
            self.state.players[i].hand = [first_cards[i], self.state.deck.draw()]

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
        If a player cannot cover the blind, they go all-in and a side pot is created.
        """
        small_blind_idx = (self.dealer + 1) % len(self.players)
        big_blind_idx = (self.dealer + 2) % len(self.players)

        small_blind_amount = min(self.state.small_blind, self.state.players[small_blind_idx].balance)
        big_blind_amount = min(self.state.big_blind, self.state.players[big_blind_idx].balance)

        # deduct from balances
        self.state.players[small_blind_idx].balance -= small_blind_amount
        self.state.players[big_blind_idx].balance -= big_blind_amount

        # add to main pot
        self.state.pots[0].amount += small_blind_amount + big_blind_amount

        # update player_bets
        self.state.player_bets[small_blind_idx] = small_blind_amount
        self.state.player_bets[big_blind_idx] = big_blind_amount

        # set current bet
        self.state.current_bet = self.state.big_blind

        # handle all-in blind cases
        if small_blind_amount < self.state.small_blind:
            self._handle_all_in(small_blind_idx, small_blind_amount)
        if big_blind_amount < self.state.big_blind:
            self._handle_all_in(big_blind_idx, big_blind_amount)

    def _handle_all_in(self, all_in_idx: int, all_in_amount: float) -> None:
        """
        Split pots when a player goes all-in.
        The all-in player can only win up to all_in_amount from each other player.
        Excess contributions go into a new side pot that the all-in player is not eligible for.
        """
        # cap the main pot contribution per player to the all-in amount
        # any excess already contributed by others goes into a side pot
        excess = 0.0
        for i in self.state.active_players:
            if i == all_in_idx:
                continue
            overpaid = max(0.0, self.state.player_bets[i] - all_in_amount)
            if overpaid > 0:
                self.state.pots[0].amount -= overpaid
                excess += overpaid

        if excess > 0:
            # create a new side pot with all players except the all-in player
            side_eligible = [i for i in self.state.active_players if i != all_in_idx]
            self.state.pots.append(Pot(amount=excess, eligible=side_eligible))

        # remove all-in player from eligibility of any future side pots
        # they remain eligible for the main pot only
        self.state.pots[0].eligible = [i for i in self.state.pots[0].eligible if
                                       i == all_in_idx or i in self.state.active_players]

        # remove all-in player from active_players so they can't act further
        if all_in_idx in self.state.active_players:
            self.state.active_players.remove(all_in_idx)

    def _showdown(self) -> None:
        for pot in self.state.pots:
            if not pot.eligible:
                continue
            # evaluate best hand for each eligible player
            hands = {
                i: best_hand(self.state.players[i], self.state)
                for i in pot.eligible
            }
            # find the best hand
            best_idx = pot.eligible[0]
            for i in pot.eligible[1:]:
                if compare_hands(hands[i], hands[best_idx]) == 1:
                    best_idx = i
            # collect winners (handle ties)
            winners = [
                i for i in pot.eligible
                if compare_hands(hands[i], hands[best_idx]) == 0
            ]
            # split pot among winners
            share = pot.amount / len(winners)
            for i in winners:
                self.state.players[i].balance += share

    def _active_players(self) -> list[Player]:
        """
        Return a list of Player objects that are active in the current game. That is, they can play more hands.
        Note: this is NOT the same as state.active_players.
            - state.active_players checks for ability to place a bet within a hand.
            - self._active_players checks for players who are able to bet anything at all.
        """
        return [p for p in self.players if p.balance > 0]