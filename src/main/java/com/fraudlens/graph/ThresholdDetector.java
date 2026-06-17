package com.fraudlens.graph;

import com.fraudlens.model.FraudAlert;
import com.fraudlens.model.Transaction;
import com.fraudlens.patterns.FraudDetector;

import java.util.*;

/**
 * Strategy Pattern implementation — ThresholdDetector is one concrete fraud detection strategy.
 *
 * Algorithm: Statistical clustering analysis on outgoing transaction amounts.
 * Detects accounts whose transactions cluster suspiciously just below a reporting
 * threshold — a classic "structuring" or "smurfing" pattern used to evade detection.
 *
 * Detection rule: if an account sends 10+ transactions and ≥60% of them fall
 * within 97–100% of the threshold, the account is flagged.
 *
 * Time complexity: O(E) where E = number of transactions (single pass).
 *
 * Liskov Substitution (SOLID-L): can replace any other FraudDetector without breaking callers.
 * Single Responsibility (SOLID-S): only responsible for threshold structuring detection.
 */
public class ThresholdDetector implements FraudDetector {

    private static final double THRESHOLD       = 49_500.0;
    private static final double MIN_RATIO       = 0.970;   // 97% of threshold
    private static final int    MIN_TRANSACTIONS = 10;      // minimum txns to consider
    private static final double MIN_CLUSTER_PCT  = 0.60;   // 60% must cluster below threshold

    @Override
    public List<FraudAlert> detect(TransactionGraph graph) {
        List<Transaction> allTxns = graph.getAllTransactions();

        // Group outgoing transactions by sender
        Map<String, List<Double>> amountsBySender = new HashMap<>();
        for (Transaction t : allTxns) {
            amountsBySender
                .computeIfAbsent(t.getFromAccount(), k -> new ArrayList<>())
                .add(t.getAmount());
        }

        List<String> flagged = new ArrayList<>();

        for (Map.Entry<String, List<Double>> entry : amountsBySender.entrySet()) {
            List<Double> amounts = entry.getValue();
            if (amounts.size() < MIN_TRANSACTIONS) continue;

            // Count how many amounts fall in the structuring band [97%–100% of threshold]
            long clusterCount = amounts.stream()
                    .filter(amt -> {
                        double ratio = amt / THRESHOLD;
                        return ratio >= MIN_RATIO && ratio <= 1.0;
                    })
                    .count();

            double clusterPct = (double) clusterCount / amounts.size();
            if (clusterPct >= MIN_CLUSTER_PCT) {
                flagged.add(entry.getKey());
            }
        }

        List<FraudAlert> alerts = new ArrayList<>();
        if (!flagged.isEmpty()) {
            alerts.add(new FraudAlert(
                "THRESHOLD",
                "Structuring detected: " + flagged.size() +
                    " account(s) clustering transactions just below ₹" +
                    String.format("%,.0f", THRESHOLD) + " reporting threshold",
                flagged
            ));
        }
        return alerts;
    }
}
