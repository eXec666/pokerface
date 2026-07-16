package com.andrei.pokerface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;


public class HandEvaluator {


   private static Map<Integer, Integer> countRanks(int[] cards) {
        // Return mapping of card poker value -> appearance count in cards
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i: cards) {
            counts.put(CardUtils.pokerValue(i), counts.getOrDefault(CardUtils.pokerValue(i), 0) + 1);
        }
        return counts;
   }
   
   private static Map<Integer, Integer> countSuits(int[] cards) {
        // Return mapping of card suit -> appearance count in cards
        Map<Integer, Integer> counts = new HashMap<>();
        for (int c : cards) {
            counts.put(CardUtils.getSuit(c), counts.getOrDefault(CardUtils.getSuit(c), 0) + 1);
        }
        return counts;
   }

   public static int[] evaluateHighCard(int[] cards) {
        int[] ranks = new int[7];
        int idx = 0;
        for (int c: cards) { 
            ranks[idx] = CardUtils.pokerValue(c);
            idx++;
        }
        // Sort (ascending order)
        Arrays.sort(ranks);

        // Return 5 highest ranks (5 last elements)
        int[] result =  Arrays.copyOfRange(ranks, ranks.length - 5, ranks.length);

        // reverse to get descending order
        for (int i = 0; i < result.length / 2; i ++) {
            int temp = result[i];
            result[i] = result[result.length - 1 - i];
            result[result.length - 1 - i] = temp;
        }
        return result;
   }

   public static int[] evaluatePair(int[] cards) {
        // compute rank of highest pair
        Map<Integer, Integer> rankCounts = countRanks(cards);
        int highestPairRank = 0;
        for (int k: rankCounts.keySet()) {
            if (rankCounts.get(k) >= 2 && k > highestPairRank) {
                highestPairRank = k;
            }
        }
        // No pair found: return empty array as sentinel.
        if (highestPairRank == 0) {
            return new int[0];
        }
        else {
            // compile kicker candidates
            List<Integer> candidateKickers = new ArrayList<>();
            for (int c : cards) {
                if (CardUtils.pokerValue(c) != highestPairRank) {
                    candidateKickers.add(CardUtils.pokerValue(c));
                }
            }
            Collections.sort(candidateKickers);
            int[] allKickers = candidateKickers.stream().mapToInt(Integer::intValue).toArray();
            // choose 3 highest kickers
            int[] kickers = Arrays.copyOfRange(allKickers, allKickers.length - 3, allKickers.length);
            int[] merged =  {highestPairRank, kickers[2], kickers[1], kickers[0]};
            return merged;
        }
   }

   public static int[] evaluateTwoPair(int [] cards) {
        // Compute list of all card ranks with at least 2 repeats
        Map<Integer, Integer> rankCounts = countRanks(cards);
        List<Integer> pairRanks = new ArrayList<>();
        for (int k: rankCounts.keySet()) {
            if (rankCounts.get(k) >= 2) {
                pairRanks.add(k);
            }
        }
        // Less than 2 distinct pairs: return empty array as sentinel
        if (pairRanks.size() < 2) {
            return new int[0];
        }
        else {
            Collections.sort(pairRanks);
            // find higher and lower pair
            int highPair = pairRanks.removeLast();
            int lowPair = pairRanks.removeLast();
            // compute candidate kickers
            List<Integer> candidateKickers = new ArrayList<>();
            for (int c : cards) {
                if (CardUtils.pokerValue(c) != highPair && CardUtils.pokerValue(c) != lowPair) {
                    candidateKickers.add(CardUtils.pokerValue(c));
                }
            }
            // take highest kicker
            int kicker = Collections.max(candidateKickers);
            int[] merged = {highPair, lowPair, kicker};
            return merged;
        }
   }

   public static int[] evaluateTriple(int[] cards) {
        Map<Integer, Integer> rankCounts = countRanks(cards);
        int highestTripRank = 0;
        for (int k: rankCounts.keySet()) {
            if (rankCounts.get(k) >= 3 && k > highestTripRank) {
                highestTripRank = k;
            }
        }
        if (highestTripRank == 0) {
            return new int[0];
        }
        else {
            List<Integer> candidateKickers = new ArrayList<>();
            for (int c : cards) {
                if (CardUtils.pokerValue(c) != highestTripRank) {
                    candidateKickers.add(CardUtils.pokerValue(c));
                }
            }
            Collections.sort(candidateKickers);
            int[] allKickers = candidateKickers.stream().mapToInt(Integer::intValue).toArray();
            // choose 2 highest kickers
            int[] kickers = {allKickers[allKickers.length - 1], allKickers[allKickers.length - 2]};
            int[] merged = {highestTripRank, kickers[0], kickers[1]};
            return merged;
        }
   }

   public static int[] evaluateStraight(int [] cards) {
        // find all unique cards by pokerValue
        Map<Integer, Integer> rankCounts = countRanks(cards);
        Set<Integer> uniqueValues = rankCounts.keySet();
        // Iterate through highest card candidates
        for (int i = 14; i > 5; i--) {
            boolean candidatePresent = uniqueValues.contains(i) && uniqueValues.contains(i-1) && 
    uniqueValues.contains(i-2) && uniqueValues.contains(i-3) && 
    uniqueValues.contains(i-4);
            if (candidatePresent) {
                int[] result = {i};
                return result;
            }
        }
        // Ace-Low edgecase
        Set<Integer> aceLowCandidate = new HashSet<>(Arrays.asList(14, 2, 3, 4, 5));
        if (uniqueValues.containsAll(aceLowCandidate)) {
            int[] result = {5};
            return result;
        }
        // No straight -> return sentinel
        else {
            return new int[0];
        }
   }

   public static int[] evaluateFlush(int[] cards) {
        // find flush suit
        Map<Integer, Integer> suitCounts = countSuits(cards);
        int flushSuit = 0;
        for (int k: suitCounts.keySet()) {
            if (suitCounts.get(k) >= 5) {
                flushSuit = k;
            }
        }
        // no suit has flush -> return sentinel
        if (flushSuit == 0) {
            return new int[0];
        }
        else {
            // Collect list of all cards with that suit
            List<Integer> flushValues = new ArrayList<>();
            for (int c: cards) {
                if (CardUtils.getSuit(c) == flushSuit) {
                    flushValues.add(CardUtils.pokerValue(c));
                }
            }
            Collections.sort(flushValues, Collections.reverseOrder());
            int[] result = new int[5];
            for (int i = 0; i < 5; i++) {
                result[i] = flushValues.get(i);
            }
            return result;
        }
   }

   public static int[] evaluateFullHouse(int[] cards) {
        Map<Integer, Integer> rankCounts = countRanks(cards);
        List<Integer> tripRanks = new ArrayList<>(), pairRanks = new ArrayList<>();
        for (int k: rankCounts.keySet()) {
            if (rankCounts.get(k) >= 2) {
                pairRanks.add(k);
            }
            if (rankCounts.get(k) >= 3) {
                tripRanks.add(k);
            }
        }
        int highestTripRank = tripRanks.size() > 0 ? Collections.max(tripRanks) : 0;
        int highestPairRank = 0;
        for (int p: pairRanks) {
            if (p > highestPairRank && p != highestTripRank) {
                highestPairRank = p;
            }
        }
        if (highestTripRank == 0 || highestPairRank == 0) {
            return new int[0];
        }
        else {
            int[] result = {highestTripRank, highestPairRank};
            return result;
        }
   }

   public static int[] evaluateQuad(int[] cards) {
        Map<Integer, Integer> rankCounts = countRanks(cards);
        int highestQuadRank = 0;
        for (int k: rankCounts.keySet()) {
            if (rankCounts.get(k) >= 4 && k > highestQuadRank) {
                highestQuadRank = k;
            }
        }
        if (highestQuadRank == 0) {
            return new int[0];
        }
        else {
            List<Integer> candidateKickers = new ArrayList<>();
            for (int c : cards) {
                if (CardUtils.pokerValue(c) != highestQuadRank) {
                    candidateKickers.add(CardUtils.pokerValue(c));
                }
            }
            Collections.sort(candidateKickers);
            int kicker = candidateKickers.removeLast();
            int[] result = {highestQuadRank, kicker};
            return result;
        }
   }

   public static int[] evaluateStraightFlush(int[] cards) {
        // find flush suit from cards
        Map<Integer, Integer> suitCounts = countSuits(cards);
        int flushSuit = 0;
        for (int k: suitCounts.keySet()) {
            if (suitCounts.get(k) >= 5) {
                flushSuit = k;
            }
        }
        // no flush present
        if (flushSuit == 0) return new int[0];

        // filter to only cards of flush suit
        List<Integer> suitedCards = new ArrayList<>();
        for (int c : cards) {
            if (CardUtils.getSuit(c) == flushSuit) {
                suitedCards.add(c);
            }
        }
        int[] suitedArray = suitedCards.stream().mapToInt(Integer::intValue).toArray();
        // check if suited cards form a straight.
        return evaluateStraight(suitedArray);
   }

   static int encodeScore(int handType, int[] ranks) {
        int score = handType << 20;
        for (int i = 0; i < 5; i++) {
            int rank = (i < ranks.length) ? ranks[i] : 0;
            score |= (rank << (4 * (4 - i)));
        }
        return score;
   }

   public static int evaluateBestHand(int[] cards) {
        // cycle through each combination in decreasing value order.

        // straight flush
        int[] sf = evaluateStraightFlush(cards);
        if (sf.length > 0) return encodeScore(8, sf);
        // quads
        int[] quad = evaluateQuad(cards);
        if (quad.length > 0) return encodeScore(7, quad);
        // full house
        int[] fh = evaluateFullHouse(cards);
        if (fh.length > 0) return encodeScore(6, fh);
        // flush
        int[] flush = evaluateFlush(cards);
        if (flush.length > 0) return encodeScore(5, flush);
        // straight
        int[] straight = evaluateStraight(cards);
        if (straight.length > 0) return encodeScore(4, straight);
        // trips
        int[] trip = evaluateTriple(cards);
        if (trip.length > 0) return encodeScore(3, trip);
        // two pair
        int[] twoPair = evaluateTwoPair(cards);
        if (twoPair.length > 0) return encodeScore(2, twoPair);
        // pair
        int[] pair = evaluatePair(cards);
        if (pair.length > 0) return encodeScore(1, pair);

        // high card (always present)
        int[] high = evaluateHighCard(cards);
        return encodeScore(0, high);
   }
}
