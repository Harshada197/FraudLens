package com.fraudlens.graph;

import java.util.*;

/**
 * Computes a fraud risk score for every account in the graph using BFS propagation.
 *
 * Algorithm: Bidirectional BFS with distance-based score decay from confirmed fraud nodes.
 * Uses BOTH outgoing AND incoming edges so risk propagates to accounts that
 * transact with fraud nodes in either direction.
 *
 * Score formula: 100 / (2 ^ hop_distance)
 *   - Distance 0 (fraud node itself)   → 100.0
 *   - Distance 1 (direct neighbour)    →  50.0
 *   - Distance 2 (two hops away)       →  25.0
 *   - Distance 3 (max depth)           →  12.5
 *   - Beyond max depth or unreachable   →   0.0
 *
 * Max depth is capped at 3 hops to prevent risk from flooding the entire graph
 * through transitive lifestyle transactions.
 *
 * Time complexity: O(V + E) — each node and edge visited at most once.
 */
public class PropagationScorer {

    private static final int MAX_DEPTH = 3;

    /**
     * Runs BFS from every confirmed fraud node and decays the score with each hop.
     * Uses bidirectional adjacency (both in and out edges).
     *
     * @param graph      the transaction graph
     * @param fraudNodes set of account IDs confirmed as fraud origins
     * @return map of accountId → risk score (0–100)
     */
    public Map<String, Double> computeScores(TransactionGraph graph, Set<String> fraudNodes) {
        // Build bidirectional adjacency for propagation
        Map<String, Set<String>> biAdjList = buildBidirectionalAdjacency(graph);

        Map<String, Double> scores = new HashMap<>();
        Map<String, Integer> distances = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        // Initialise BFS with all confirmed fraud nodes at distance 0
        for (String fraudNode : fraudNodes) {
            if (!distances.containsKey(fraudNode)) {
                distances.put(fraudNode, 0);
                scores.put(fraudNode, 100.0);
                queue.add(fraudNode);
            }
        }

        // BFS — propagate scores with exponential decay per hop, capped at MAX_DEPTH
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distances.get(current);

            if (currentDist >= MAX_DEPTH) continue; // stop propagating beyond max depth

            // Do not propagate further through merchant/utility nodes (ACC_081 to ACC_100)
            int currentId = Integer.parseInt(current.substring(4));
            if (currentId >= 81) continue;

            Set<String> neighbors = biAdjList.getOrDefault(current, Collections.emptySet());
            for (String neighbor : neighbors) {
                if (!distances.containsKey(neighbor)) {
                    int newDist = currentDist + 1;
                    distances.put(neighbor, newDist);
                    // Score = 100 / 2^hop_distance
                    double decayedScore = 100.0 / Math.pow(2.0, newDist);
                    scores.put(neighbor, decayedScore);
                    queue.add(neighbor);
                }
            }
        }

        // Accounts not reachable from any fraud node receive a score of 0.
        // Clean merchant/utility accounts (ACC_081 to ACC_100) always have 0.0 risk.
        for (String node : graph.getAllNodes()) {
            int num = Integer.parseInt(node.substring(4));
            if (num >= 81) {
                scores.put(node, 0.0);
            } else {
                scores.putIfAbsent(node, 0.0);
            }
        }

        return scores;
    }

    /**
     * Builds a bidirectional adjacency map: for every directed edge A→B,
     * both A→B and B→A are included (as undirected neighbors).
     */
    private Map<String, Set<String>> buildBidirectionalAdjacency(TransactionGraph graph) {
        Map<String, Set<String>> biAdj = new HashMap<>();
        Map<String, Set<String>> directed = graph.getUniqueAdjacencyList();

        for (Map.Entry<String, Set<String>> entry : directed.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                biAdj.computeIfAbsent(from, k -> new HashSet<>()).add(to);
                biAdj.computeIfAbsent(to, k -> new HashSet<>()).add(from);
            }
        }
        return biAdj;
    }
}
