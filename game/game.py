"""#TODO: Module Description"""
from typing import Optional
import logging
from players.player import Player, ActionType
from game.game_state import GameState, Pot, Street
from game.evaluator import best_hand, compare_hands

# logger setup
logger = logging.getLogger(__name__)

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
        - small_blind: small blind amount (in cents)
        - big_blind: big blind amount (in cents)
        - buy_in: starting balance for all players (in cents)
        - state: The game's current GameState, None between hands

    Representation Invariants:
        -
    """
    players: list[Player]
    dealer: int
    small_blind: int
    big_blind: int
    buy_in: int
    state: Optional[GameState]

    def __init__(self, players: list[Player], small_blind: int, big_blind: int, buy_in: int) -> None:
        self.players = players
        self.dealer = 0
        self.small_blind = small_blind
        self.big_blind = big_blind
        self.buy_in = buy_in
        self.state = None

        # Set player balances
        for player in self.players:
            player.balance = buy_in

        # configure logging
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s [%(levelname)s] %(message)s',
            datefmt='%H:%M:%S'
        )

    def run(self) -> None:
        """
        Run the session until one player remains
        """
        # log start of the game
        logger.info("=" * 50)
        logger.info("GAME STARTED")
        logger.info("=" * 50)
        logger.info(f"Players:         {len(self.players)}")
        logger.info(f"Buy-in:           {self.buy_in / 100:.2f}")
        logger.info(f"Small blind:      {self.small_blind / 100:.2f}")
        logger.info(f"Big blind:        {self.big_blind / 100:.2f}")
        logger.info("=" * 50)

        while len(self._active_players()) > 1:
            self._play_hand()
            self.dealer = (self.dealer + 1) % len(self.players)

    def _reset_street(self) -> None:
        """Reset betting state for a new street."""
        self.state.current_bet = 0
        self.state.last_raise_amount = self.state.big_blind
        self.state.player_bets = {i: 0 for i in range(len(self.state.players))}

    def _add_to_pot(self, player_idx: int, amount: int) -> None:
        """Add amount to the highest eligible pot for the player."""
        for pot in reversed(self.state.pots):
            if player_idx in pot.eligible:
                pot.amount += amount
                return
        # Should never happen: player is always eligible for at least the main pot
        self.state.pots[0].amount += amount

    def _log_pots(self) -> None:
        pots_str = " | ".join(
            f"Pot {idx}: {p.amount / 100:.2f} (eligible {p.eligible})" for idx, p in enumerate(self.state.pots))
        logger.info(f"Pots: {pots_str}")

    def _set_first_actor(self) -> None:
        n = len(self.state.active_players)
        dealer_idx = self.state.curr_dealer
        # Find dealer position in active_players
        try:
            dealer_pos = self.state.active_players.index(dealer_idx)
        except ValueError:
            # dealer not active – start from first active player
            self.state.curr_actor = 0
            return

        if self.state.street == Street.PREFLOP:
            if n == 2:
                # heads-up: small blind acts first (dealer+1)
                self.state.curr_actor = (dealer_pos + 1) % n
            else:
                # multi-way: UTG = dealer+3
                self.state.curr_actor = (dealer_pos + 3) % n
        else:
            # post-flop: first active player to the left of the button
            self.state.curr_actor = (dealer_pos + 1) % n

    def _play_hand(self) -> None:
        """
        #TODO: Docstring
        """
        for player in self.players:
            player.hand = None
            logger.debug(f"Player {self.players.index(player)} balance reset to: {player.balance}")
        self.state = GameState(self.players, self.dealer, self.small_blind, self.big_blind)
        # remove players with no balance from the hand
        self.state.active_players = [i for i in self.state.active_players
                                     if self.state.players[i].balance > 0]
        for pot in self.state.pots:
            pot.eligible = [i for i in pot.eligible if i in self.state.active_players]

        n = len(self.state.active_players)
        dealer_pos = self.state.active_players.index(self.dealer) if self.dealer in self.state.active_players else 0

        if n == 2:
            # Heads-up: preflop small blind acts first; postflop button (dealer) acts first
            if self.state.street == Street.PREFLOP:
                # Small blind is dealer+1
                sb_index_in_active = (dealer_pos + 1) % n
                self.state.curr_actor = sb_index_in_active
            else:
                # On flop and later, button (dealer) acts first
                self.state.curr_actor = dealer_pos
        else:
            # Multi-way: under the gun is dealer+3 (for preflop)
            self.state.curr_actor = (dealer_pos + 3) % n

        logger.info(f"Received game state | "
                    f"Dealer: Player {self.dealer} | "
                    f"Current actor: Player {self.state.active_players[self.state.curr_actor]}")

        self._post_blinds()
        self._deal_hole_cards()
        self._set_first_actor()
        logger.info(f"--- {self.state.street} --- Active players: {self.state.active_players} | "
                    f"Current bet: {self.state.current_bet / 100:.2f} | Pot total: {self.state.pot / 100:.2f}")
        self._log_pots()
        self._betting_round() #Preflop
        if len(self.state.active_players) <= 1:
            self._showdown()
            return

        self._reset_street()
        self._deal_community_cards(3, Street.FLOP)
        self.state.street = Street.FLOP
        self._set_first_actor()
        logger.info(f"--- {self.state.street} --- Active players: {self.state.active_players} | "
                    f"Current bet: {self.state.current_bet / 100:.2f} | Pot total: {self.state.pot / 100:.2f}")
        self._log_pots()
        self._betting_round()
        if len(self.state.active_players) <= 1:
            self._showdown()
            return

        self._reset_street()
        self._deal_community_cards(1, Street.TURN)
        self.state.street = Street.TURN
        self._set_first_actor()
        logger.info(f"--- {self.state.street} --- Active players: {self.state.active_players} | "
                    f"Current bet: {self.state.current_bet / 100:.2f} | Pot total: {self.state.pot / 100:.2f}")
        self._log_pots()
        self._betting_round()
        if len(self.state.active_players) <= 1:
            self._showdown()
            return

        self._reset_street()
        self._deal_community_cards(1, Street.RIVER)
        self.state.street = Street.RIVER
        self._set_first_actor()
        logger.info(f"--- {self.state.street} --- Active players: {self.state.active_players} | "
                    f"Current bet: {self.state.current_bet / 100:.2f} | Pot total: {self.state.pot / 100:.2f}")
        self._log_pots()
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
            logger.info(f" {len(self.state.active_players)} Player(s) remaining. No bets to make")
            return

        # wrap curr_actor in case active_players shrank since last round
        self.state.curr_actor %= len(self.state.active_players)

        # track the last player who raised
        last_aggressor = self.state.active_players[self.state.curr_actor]

        while True:
            folded = False
            actor_idx = self.state.active_players[self.state.curr_actor]
            actor = self.state.players[actor_idx]
            total_bet = self.state.player_bets[actor_idx]
            action = actor.get_action(self.state)
            logger.info(f"Player {actor_idx} {action.action_type.value} "
                        f"{action.amount / 100:.2f} | stack: {actor.balance / 100:.2f} | "
                        f"total bet this street: {self.state.player_bets[actor_idx] / 100:.2f}")

            # Folding
            if action.action_type == ActionType.FOLD:
                self.state.active_players.pop(self.state.curr_actor)
                # Remove player from all pots' eligible lists
                for pot in self.state.pots:
                    if actor_idx in pot.eligible:
                        pot.eligible.remove(actor_idx)
                logger.info(f"Player {actor_idx} removed from active_players and pots")
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
                self._add_to_pot(actor_idx, action.amount)
                self._log_pots()
                if actor.balance == 0:
                    self._handle_all_in(actor_idx, self.state.player_bets[actor_idx])
                    folded = True  # treat as removed from active players

            # Raising
            elif action.action_type == ActionType.RAISE:
                increment = (total_bet + action.amount) - self.state.current_bet
                # All-in is always allowed
                if action.amount < actor.balance and increment < self.state.last_raise_amount:
                    raise IllegalActionError(f"Minimum raise increment is {self.state.last_raise_amount}")
                if action.amount > actor.balance:
                    raise IllegalActionError("Cannot bet more than your current balance")
                # Only update last_raise_amount if this is a full raise (not all-in short)
                if action.amount == actor.balance and increment < self.state.last_raise_amount:
                    # This is a short all-in, do not change the last raise amount
                    pass
                else:
                    self.state.last_raise_amount = increment
                self.state.current_bet = total_bet + action.amount
                last_aggressor = actor_idx
                actor.balance -= action.amount
                self.state.player_bets[actor_idx] += action.amount
                self._add_to_pot(actor_idx, action.amount)
                self._log_pots()
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
        active_set = set(self.state.active_players)
        start = (self.dealer + 1) % len(self.state.players)
        # Build order of all players, but only deal to active ones
        order = list(range(start, len(self.state.players))) + list(range(0, start))
        order = [i for i in order if i in active_set]  # keep only active players

        # Deal first cards
        first_cards = {}
        for i in order:
            first_cards[i] = self.state.deck.draw()

        # Deal second cards and assign hands
        for i in order:
            self.state.players[i].hand = [first_cards[i], self.state.deck.draw()]
        logger.info(f"Dealt hole cards to {len(order)} active players.")
        for i in order:
            c1, c2 = self.state.players[i].hand
            # Convert card tuples to readable format, e.g., (1,1) -> "Ace of clubs"
            suit_map = {1: "♣", 2: "♦", 3: "♥", 4: "♠"}
            rank_map = {1: "A", 11: "J", 12: "Q", 13: "K"}

            def card_str(card):
                rank = rank_map.get(card[0], str(card[0]))
                suit = suit_map[card[1]]
                return f"{rank}{suit}"

            logger.info(f"  Player {i} cards: {card_str(c1)} {card_str(c2)}")

    def _deal_community_cards(self, n: int, street: Street) -> None:
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

        logger.info(f"Dealt {street} | Table: {self.state.table}")

    def _post_blinds(self) -> None:
        """
        Mutate GameState to process the small and big blind logic.
        If a player cannot cover the blind, they go all-in and a side pot is created.
        """
        small_blind_idx = (self.dealer + 1) % len(self.players)
        big_blind_idx = (self.dealer + 2) % len(self.players)

        # Guard: ensure both blind players are still active (should be true, but be safe)
        if small_blind_idx not in self.state.active_players or big_blind_idx not in self.state.active_players:
            logger.warning("Blind player not active – cannot post blinds. Hand aborted.")
            return

        small_blind_amount = min(self.state.small_blind, self.state.players[small_blind_idx].balance)
        big_blind_amount = min(self.state.big_blind, self.state.players[big_blind_idx].balance)

        # deduct from balances
        self.state.players[small_blind_idx].balance -= small_blind_amount
        self.state.players[big_blind_idx].balance -= big_blind_amount

        # add to main pot
        self._add_to_pot(small_blind_idx, small_blind_amount)
        self._add_to_pot(big_blind_idx, big_blind_amount)

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

        self._log_pots()
        non_empty_pots = [p for p in self.state.pots if p.amount > 0]
        logger.info(f"Blinds posted | "
                    f"Player {small_blind_idx} paid: {small_blind_amount / 100:.2f} | "
                    f"Player {big_blind_idx} paid: {big_blind_amount / 100:.2f} | "
                    f"Pot: {self.state.pot / 100:.2f}")
        logger.info(f"All non-empty Pots: {non_empty_pots}")




    def _handle_all_in(self, all_in_idx: int, all_in_amount: float) -> None:
        """
        Split pots when a player goes all-in.
        The all-in player can only win up to all_in_amount from each other player.
        Excess contributions go into a new side pot that the all-in player is not eligible for.
        """
        # all_in_amount is the total amount this player has contributed this street (including current bet)
        # We need to split each existing pot that this player is eligible for.
        new_pots = []
        for pot in self.state.pots:
            if all_in_idx not in pot.eligible:
                new_pots.append(pot)
                continue
            # This pot will be split into a "main" portion (capped at all_in_amount per player)
            # and a "side" portion (excess above all_in_amount from others)
            main_amount = 0
            side_amount = 0
            main_eligible = [all_in_idx]
            side_eligible = []
            for i in pot.eligible:
                bet_i = self.state.player_bets[i]
                if i == all_in_idx:
                    main_amount += bet_i
                else:
                    if bet_i <= all_in_amount:
                        main_amount += bet_i
                        main_eligible.append(i)
                    else:
                        main_amount += all_in_amount
                        side_amount += (bet_i - all_in_amount)
                        side_eligible.append(i)
            # Add main pot
            if main_amount > 0:
                new_pots.append(Pot(amount=main_amount, eligible=main_eligible))
            # Add side pot
            if side_amount > 0:
                # Side pot is contested only by those who bet more than all_in_amount
                new_pots.append(Pot(amount=side_amount, eligible=side_eligible))
        # Replace pots
        self.state.pots = new_pots
        self._log_pots()
        # Remove all-in player from active_players
        if all_in_idx in self.state.active_players:
            self.state.active_players.remove(all_in_idx)

    def _showdown(self) -> None:
        logger.info("Showdown Initiated.")
        for pot in self.state.pots:
            if not pot.eligible:
                continue
            # Filter out players who have no hand (folded or not dealt)
            eligible_with_hand = [
                i for i in pot.eligible
                if self.state.players[i].hand is not None
            ]
            if not eligible_with_hand:
                continue  # no one can win this pot (should not happen)

            # Evaluate best hand for each eligible player
            hands = {
                i: best_hand(self.state.players[i], self.state)
                for i in eligible_with_hand
            }
            for player, hand in hands.items():
                logger.info(f"Player {player}'s best hand: {hand}")

            # Find the best hand among eligible_with_hand
            best_idx = eligible_with_hand[0]
            for i in eligible_with_hand[1:]:
                if compare_hands(hands[i], hands[best_idx]) == 1:
                    best_idx = i

            # Collect winners (handle ties)
            winners = [
                i for i in eligible_with_hand
                if compare_hands(hands[i], hands[best_idx]) == 0
            ]
            logger.info(f"Player(s) {winners} won this pot (amount: {pot.amount / 100:.2f})")

            # Split the pot among winners
            share = pot.amount // len(winners)
            remainder = pot.amount % len(winners)
            for idx, winner_idx in enumerate(winners):
                bonus = 1 if idx < remainder else 0
                self.state.players[winner_idx].balance += share + bonus

        # After all pots have been awarded, log final balances and hand separator
        logger.info(f"New player balances: {[p.balance / 100 for p in self.players]}")
        logger.info("-" * 50)
        logger.info("Hand finished.")
        logger.info("=" * 50)

    def _active_players(self) -> list[Player]:
        """
        Return a list of Player objects that are active in the current game. That is, they can play more hands.
        Note: this is NOT the same as state.active_players.
            - state.active_players checks for ability to place a bet within a hand.
            - self._active_players checks for players who are able to bet anything at all.
        """
        return [p for p in self.players if p.balance > 0]

if __name__ == "__main__":
    from players.player import CheckPlayer
    players = [CheckPlayer() for _ in range(3)]
    game = Game(players, small_blind=1, big_blind=2, buy_in=100)
    game.run()
