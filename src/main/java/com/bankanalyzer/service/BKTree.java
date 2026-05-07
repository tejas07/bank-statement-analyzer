package com.bankanalyzer.service;

import com.bankanalyzer.model.Category;

import java.util.*;

/**
 * BK-Tree for approximate merchant name matching using Levenshtein distance.
 * Average O(log n) lookup vs O(n) brute-force for fuzzy category tagging.
 */
public class BKTree {

    private static final class Node {
        final String word;
        final Category category;
        final Map<Integer, Node> children = new HashMap<>();

        Node(String word, Category category) {
            this.word = word;
            this.category = category;
        }
    }

    private Node root;

    public void insert(String word, Category category) {
        if (word == null || word.isBlank()) return;
        if (root == null) {
            root = new Node(word, category);
            return;
        }
        Node current = root;
        while (true) {
            int dist = levenshtein(word, current.word);
            if (dist == 0) return; // duplicate
            Node child = current.children.get(dist);
            if (child == null) {
                current.children.put(dist, new Node(word, category));
                return;
            }
            current = child;
        }
    }

    /**
     * Returns the best-matching category within maxDistance, or empty if none found.
     */
    public Optional<Category> search(String query, int maxDistance) {
        if (root == null || query == null || query.isBlank()) return Optional.empty();

        String[] best = new String[]{null};
        Category[] bestCategory = new Category[]{null};
        int[] bestDist = {maxDistance + 1};

        searchRecursive(root, query, maxDistance, best, bestCategory, bestDist);
        return bestDist[0] <= maxDistance ? Optional.of(bestCategory[0]) : Optional.empty();
    }

    private void searchRecursive(Node node, String query, int maxDistance,
                                  String[] best, Category[] bestCategory, int[] bestDist) {
        int dist = levenshtein(query, node.word);
        if (dist < bestDist[0]) {
            bestDist[0] = dist;
            best[0] = node.word;
            bestCategory[0] = node.category;
        }
        // Only explore children within the metric ball [dist - maxDistance, dist + maxDistance]
        int lo = dist - maxDistance;
        int hi = dist + maxDistance;
        for (Map.Entry<Integer, Node> entry : node.children.entrySet()) {
            if (entry.getKey() >= lo && entry.getKey() <= hi) {
                searchRecursive(entry.getValue(), query, maxDistance, best, bestCategory, bestDist);
            }
        }
    }

    // Optimised two-row Levenshtein — O(m*n) time, O(min(m,n)) space
    static int levenshtein(String a, String b) {
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        // Ensure a is the shorter string to minimise memory
        if (a.length() > b.length()) { String t = a; a = b; b = t; }

        int[] prev = new int[a.length() + 1];
        int[] curr = new int[a.length() + 1];
        for (int i = 0; i <= a.length(); i++) prev[i] = i;

        for (int j = 1; j <= b.length(); j++) {
            curr[0] = j;
            for (int i = 1; i <= a.length(); i++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[i] = Math.min(Math.min(curr[i - 1] + 1, prev[i] + 1), prev[i - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[a.length()];
    }
}
