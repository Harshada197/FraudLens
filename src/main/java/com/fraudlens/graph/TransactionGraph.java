package com.fraudlens.graph;

import com.fraudlens.model.Transaction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Singleton Pattern: single shared instance across all fraud detectors.
 * Builds and maintains the directed adjacency list of UPI transactions.
 */
public class TransactionGraph {

    // Adjacency list: fromAccount -> list of toAccounts
    // Data structure: HashMap<String, List<String>>
    // O(1) average lookup per node
    private HashMap<String, List<String>> adjacencyList;

    // De-duplicated adjacency list (unique edges only)
    private HashMap<String, Set<String>> uniqueEdges;

    // Raw transaction list kept for detectors that need timestamps
    private List<Transaction> transactions;

    public TransactionGraph() {
        this.adjacencyList = new HashMap<>();
        this.uniqueEdges = new HashMap<>();
        this.transactions = new ArrayList<>();
    }

    /**
     * Builds adjacency list from the provided transaction list.
     * Time complexity: O(E) where E = number of transactions
     */
    public void buildGraph(List<Transaction> transactions) {
        this.transactions = new ArrayList<>(transactions);
        this.adjacencyList = new HashMap<>();
        this.uniqueEdges = new HashMap<>();

        for (Transaction t : transactions) {
            String from = t.getFromAccount();
            String to = t.getToAccount();

            // Build adjacency list (allows duplicates — mirrors real edges)
            adjacencyList.computeIfAbsent(from, k -> new ArrayList<>()).add(to);

            // Ensure destination node exists in the map (even if it never sends)
            adjacencyList.putIfAbsent(to, new ArrayList<>());

            // Track unique edges for graph rendering
            uniqueEdges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
            uniqueEdges.putIfAbsent(to, new HashSet<>());
        }
    }

    /** Returns the full adjacency list (may contain duplicate edges). */
    public HashMap<String, List<String>> getAdjacencyList() {
        return adjacencyList;
    }

    /** Returns adjacency list with unique edges only (for cycle/hub detection). */
    public HashMap<String, Set<String>> getUniqueAdjacencyList() {
        return uniqueEdges;
    }

    /** Returns all transactions between two specific accounts. O(E). */
    public List<Transaction> getTransactionsBetween(String from, String to) {
        return transactions.stream()
                .filter(t -> t.getFromAccount().equals(from) && t.getToAccount().equals(to))
                .collect(Collectors.toList());
    }

    /** Returns the full transaction list for detectors that need timestamps. */
    public List<Transaction> getAllTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    /** Returns all known node IDs. */
    public Set<String> getAllNodes() {
        return adjacencyList.keySet();
    }
}
