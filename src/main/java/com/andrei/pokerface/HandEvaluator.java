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

   /**
    * Human-readable description of the best hand in cards, e.g. "Full House,
    * Aces full of Kings". Mirrors evaluateBestHand's exact cascade order so
    * the two never disagree about which hand type wins -- this is a pure
    * text-formatting layer on top of the same evaluate* results, not a
    * separate scoring path.
    */
   public static String describeBestHand(int[] cards) {
        int[] sf = evaluateStraightFlush(cards);
        if (sf.length > 0) return "Straight Flush, " + rankName(sf[0]) + " High";

        int[] quad = evaluateQuad(cards);
        if (quad.length > 0) return "Four of a Kind, " + rankNamePlural(quad[0]);

        int[] fh = evaluateFullHouse(cards);
        if (fh.length > 0) return "Full House, " + rankNamePlural(fh[0]) + " full of " + rankNamePlural(fh[1]);

        int[] flush = evaluateFlush(cards);
        if (flush.length > 0) return "Flush, " + rankName(flush[0]) + " High";

        int[] straight = evaluateStraight(cards);
        if (straight.length > 0) return "Straight, " + rankName(straight[0]) + " High";

        int[] trip = evaluateTriple(cards);
        if (trip.length > 0) return "Three of a Kind, " + rankNamePlural(trip[0]);

        int[] twoPair = evaluateTwoPair(cards);
        if (twoPair.length > 0) return "Two Pair, " + rankNamePlural(twoPair[0]) + " and " + rankNamePlural(twoPair[1]);

        int[] pair = evaluatePair(cards);
        if (pair.length > 0) return "Pair of " + rankNamePlural(pair[0]);

        int[] high = evaluateHighCard(cards);
        return "High Card, " + rankName(high[0]);
   }

   private static String rankName(int pokerValue) {
        return switch (pokerValue) {
            case 14 -> "Ace";
            case 13 -> "King";
            case 12 -> "Queen";
            case 11 -> "Jack";
            case 10 -> "Ten";
            case 9 -> "Nine";
            case 8 -> "Eight";
            case 7 -> "Seven";
            case 6 -> "Six";
            case 5 -> "Five";
            case 4 -> "Four";
            case 3 -> "Three";
            case 2 -> "Two";
            default -> String.valueOf(pokerValue);
        };
   }

   private static String rankNamePlural(int pokerValue) {
        return switch (pokerValue) {
            case 14 -> "Aces";
            case 13 -> "Kings";
            case 12 -> "Queens";
            case 11 -> "Jacks";
            case 10 -> "Tens";
            case 9 -> "Nines";
            case 8 -> "Eights";
            case 7 -> "Sevens";
            case 6 -> "Sixes";
            case 5 -> "Fives";
            case 4 -> "Fours";
            case 3 -> "Threes";
            case 2 -> "Twos";
            default -> pokerValue + "s";
        };
   }
}

