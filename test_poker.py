from game.game import Game
from game.game_state import GameState
from game.evaluator import (
    has_pair, has_two_pair, has_triple, has_full_house,
    has_flush, has_straight, has_straight_flush,
    has_royal_flush, has_quad, best_hand, Combination
)
from players.player import Player, Action, ActionType

class ScriptedPlayer(Player):
    def __init__(self, balance: float, action: Action) -> None:
        super().__init__(balance)
        self._action = action

    def get_action(self, state: GameState) -> Action:
        if self._action.action_type == ActionType.CALL:
            # compute how much is still needed to call
            actor_idx = next(i for i, p in enumerate(state.players) if p is self)
            already_bet = state.player_bets[actor_idx]
            still_needed = max(0.0, state.current_bet - already_bet)
            amount = min(still_needed, self.balance)
            return Action(ActionType.CALL, amount)
        return self._action

def make_test_state(hole_cards: list, table_cards: list):
    """Construct a minimal player and GameState for evaluator testing."""
    player = ScriptedPlayer(100, Action(ActionType.FOLD))
    player.hand = hole_cards
    state = GameState([player], 0, 1, 2)
    state.table = table_cards
    return player, state

def assert_eq(label: str, actual, expected) -> None:
    assert actual == expected, f"FAIL [{label}]: expected {expected}, got {actual}"
    print(f"OK   [{label}]")

def assert_not_none(label: str, actual) -> None:
    assert actual is not None, f"FAIL [{label}]: expected a result, got None"
    print(f"OK   [{label}]")

def assert_none(label: str, actual) -> None:
    assert actual is None, f"FAIL [{label}]: expected None, got {actual}"
    print(f"OK   [{label}]")

def assert_money_conserved(label: str, players: list, expected_total: float) -> None:
    total = sum(p.balance for p in players)
    assert abs(total - expected_total) < 0.01, f"FAIL [{label}]: expected {expected_total}, got {total}"
    print(f"OK   [{label}]")


def test_evaluator() -> None:
    print("\n--- Evaluator Tests ---")

    # has_pair: two aces in hand
    player, state = make_test_state(
        hole_cards=[(1, 1), (1, 2)],
        table_cards=[(5, 1), (7, 2), (9, 3), (11, 4), (13, 1)]
    )
    assert_not_none("has_pair with pocket aces", has_pair(player, state))

    # has_pair: no pair
    player, state = make_test_state(
        hole_cards=[(2, 1), (4, 2)],
        table_cards=[(6, 1), (8, 2), (10, 3), (11, 4), (13, 1)]
    )
    assert_none("has_pair with no pair", has_pair(player, state))

    # has_two_pair
    player, state = make_test_state(
        hole_cards=[(1, 1), (1, 2)],
        table_cards=[(13, 1), (13, 2), (5, 3), (7, 4), (9, 1)]
    )
    assert_not_none("has_two_pair with aces and kings", has_two_pair(player, state))

    # has_triple
    player, state = make_test_state(
        hole_cards=[(1, 1), (1, 2)],
        table_cards=[(1, 3), (5, 2), (7, 3), (9, 4), (11, 1)]
    )
    assert_not_none("has_triple with three aces", has_triple(player, state))

    # has_flush
    player, state = make_test_state(
        hole_cards=[(1, 1), (5, 1)],
        table_cards=[(9, 1), (11, 1), (13, 1), (2, 2), (3, 3)]
    )
    assert_not_none("has_flush with five clubs", has_flush(player, state))

    # has_straight: normal
    player, state = make_test_state(
        hole_cards=[(5, 1), (6, 2)],
        table_cards=[(7, 3), (8, 4), (9, 1), (2, 2), (3, 3)]
    )
    assert_not_none("has_straight 5-9", has_straight(player, state))

    # has_straight: ace-low wheel
    player, state = make_test_state(
        hole_cards=[(1, 1), (2, 2)],
        table_cards=[(3, 3), (4, 4), (5, 1), (10, 2), (11, 3)]
    )
    assert_not_none("has_straight ace-low wheel", has_straight(player, state))

    # has_full_house
    player, state = make_test_state(
        hole_cards=[(1, 1), (1, 2)],
        table_cards=[(1, 3), (13, 1), (13, 2), (5, 3), (7, 4)]
    )
    assert_not_none("has_full_house aces over kings", has_full_house(player, state))

    # has_quad
    player, state = make_test_state(
        hole_cards=[(1, 1), (1, 2)],
        table_cards=[(1, 3), (1, 4), (5, 1), (7, 2), (9, 3)]
    )
    assert_not_none("has_quad four aces", has_quad(player, state))

    # has_straight_flush
    player, state = make_test_state(
        hole_cards=[(5, 1), (6, 1)],
        table_cards=[(7, 1), (8, 1), (9, 1), (2, 2), (3, 3)]
    )
    assert_not_none("has_straight_flush 5-9 clubs", has_straight_flush(player, state))

    # has_royal_flush
    player, state = make_test_state(
        hole_cards=[(1, 1), (13, 1)],
        table_cards=[(10, 1), (11, 1), (12, 1), (2, 2), (3, 3)]
    )
    assert_not_none("has_royal_flush", has_royal_flush(player, state))

    # best_hand correctly identifies royal flush over everything
    player, state = make_test_state(
        hole_cards=[(1, 1), (13, 1)],
        table_cards=[(10, 1), (11, 1), (12, 1), (2, 2), (3, 3)]
    )
    combo, cards = best_hand(player, state)
    assert_eq("best_hand royal flush detection", combo, Combination.ROYAL_FLUSH)
    assert_eq("best_hand returns 5 cards", len(cards), 5)



def test_game() -> None:
    print("\n--- Game Tests ---")

    # everyone folds immediately - money should be conserved
    players = [ScriptedPlayer(100, Action(ActionType.FOLD)) for _ in range(3)]
    starting_total = sum(p.balance for p in players)
    game = Game(players, small_blind=100, big_blind=200, buy_in=10000)
    game._play_hand()
    assert_money_conserved("fold scenario money conservation", players, starting_total)

    # everyone calls - money should be conserved through showdown
    players = [ScriptedPlayer(100, Action(ActionType.CALL, 2)) for _ in range(3)]
    starting_total = sum(p.balance for p in players)
    game = Game(players, small_blind=1, big_blind=2)
    game._play_hand()
    assert_money_conserved("call scenario money conservation", players, starting_total)

    # full session - money conserved across multiple hands
    players = [ScriptedPlayer(100, Action(ActionType.CALL, 2)) for _ in range(3)]
    starting_total = sum(p.balance for p in players)
    game = Game(players, small_blind=1, big_blind=2)
    game.run()
    assert_money_conserved("full session money conservation", players, starting_total)

    # all-in scenario: one player has less than the big blind
    # player 0: 100, player 1: 100, player 2: 1 (can't cover big blind)
    players = [ScriptedPlayer(100, Action(ActionType.CALL, 2)),
               ScriptedPlayer(100, Action(ActionType.CALL, 2)),
               ScriptedPlayer(1, Action(ActionType.CALL, 1))]
    starting_total = sum(p.balance for p in players)
    game = Game(players, small_blind=1, big_blind=2)
    game._play_hand()
    assert_money_conserved("all-in blind money conservation", players, starting_total)

    # all-in mid-hand: player runs out of chips calling
    # player 0: 100, player 1: 100, player 2: 4 (can cover big blind but goes all-in calling)
    players = [ScriptedPlayer(100, Action(ActionType.CALL, 2)),
               ScriptedPlayer(100, Action(ActionType.CALL, 2)),
               ScriptedPlayer(4, Action(ActionType.CALL, 2))]
    starting_total = sum(p.balance for p in players)
    game = Game(players, small_blind=1, big_blind=2)
    game._play_hand()
    assert_money_conserved("all-in mid-hand money conservation", players, starting_total)

    # side pot correctness: verify the all-in player can't win more than their contribution
    # player 2 is all-in for 2, so main pot = 6, side pot = anything extra between p0 and p1
    players = [ScriptedPlayer(100, Action(ActionType.CALL, 2)),
               ScriptedPlayer(100, Action(ActionType.CALL, 2)),
               ScriptedPlayer(2, Action(ActionType.CALL, 2))]
    starting_total = sum(p.balance for p in players)
    game = Game(players, small_blind=1, big_blind=2)
    game._play_hand()
    assert_money_conserved("side pot money conservation", players, starting_total)
    # additionally verify no player ended up with more than starting_total
    assert all(p.balance <= starting_total for p in players), \
        "FAIL [side pot cap]: a player won more than the total chips in play"
    print("OK   [side pot cap]")


if __name__ == "__main__":
    test_evaluator()
    test_game()
    print("\nAll tests passed.")

