package com.andrei.pokerface;

import java.util.function.LongFunction;

/**
 * Pairs a bot name with a factory that builds a fresh PokerAgent instance
 * from a given seed. Used wherever a bot's policy is itself seed-driven
 * (e.g. RandomAgent): measuring one fixed seed for an entire batch measures
 * one deterministic trajectory, not the policy's long-run behavior.
 * BotRingStatsCollector.collectWithSeedRotation() periodically reconstructs
 * the agent from a fresh seed, turning the batch into an average over many
 * independent realizations instead.
 *
 * For a deterministic bot with no meaningful seed (AlwaysCallAgent,
 * FoldingAgent), the factory simply ignores its argument, e.g.
 * seed -> new AlwaysCallAgent().
 */
public record NamedAgentFactory(String name, LongFunction<PokerAgent> factory) {
    public NamedAgentFactory {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bot name cannot be blank");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Factory cannot be null");
        }
    }
}