package com.fraudlens.graph;

import com.fraudlens.model.FraudAlert;
import com.fraudlens.model.Transaction;
import com.fraudlens.patterns.FraudDetector;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategy Pattern implementation — RapidHopDetector is one concrete fraud detection strategy.
 *
 * Algorithm: Sliding Window (two-pointer) on timestamp-sorted transactions.
 * Detects accounts that send 3 or more transactions within any 5-minute window —
 * a hallmark of rapid fund layering (money mule chains).
 *
 * Filters to transactions of at least ₹10,000 to completely avoid false positives from
 * normal lifestyle payments (which never exceed ₹5,000).
 */
public class RapidHopDetector implements FraudDetector {

    private static final long WINDOW_MINUTES = 5;
    private static final int MIN_TRANSACTIONS_IN_WINDOW = 3;
    private static final double MIN_TXN_AMOUNT = 10000.0;

    @Override
    public List<FraudAlert> detect(TransactionGraph graph) {
        List<Transaction> allTxns = graph.getAllTransactions();

        // Group transactions by sender account
        Map<String, List<Transaction>> byAccount = new HashMap<>();
        for (Transaction t : allTxns) {
            byAccount.computeIfAbsent(t.getFromAccount(), k -> new ArrayList<>()).add(t);
        }

        Set<String> flagged = new LinkedHashSet<>();

        for (Map.Entry<String, List<Transaction>> entry : byAccount.entrySet()) {
            List<Transaction> txns = entry.getValue();

            // Filter transactions to only high-value ones (>= ₹10,000) to avoid lifestyle false positives
            List<Transaction> highValueTxns = txns.stream()
                    .filter(t -> t.getAmount() >= MIN_TXN_AMOUNT)
                    .collect(Collectors.toList());

            if (highValueTxns.size() < MIN_TRANSACTIONS_IN_WINDOW) continue;

            // Sort by timestamp for sliding window — O(n log n)
            highValueTxns.sort(Comparator.comparing(Transaction::getTimestamp));

            // Sliding window with two pointers — O(n)
            int left = 0;
            for (int right = 0; right < highValueTxns.size(); right++) {
                // Shrink the left boundary while the window exceeds 5 minutes
                while (Duration.between(
                        highValueTxns.get(left).getTimestamp(),
                        highValueTxns.get(right).getTimestamp()).toMinutes() > WINDOW_MINUTES) {
                    left++;
                }

                // Window [left..right] is within 5 minutes
                if ((right - left + 1) >= MIN_TRANSACTIONS_IN_WINDOW) {
                    flagged.add(entry.getKey());
                    break; // one flag per account is sufficient
                }
            }
        }

        List<FraudAlert> alerts = new ArrayList<>();
        if (!flagged.isEmpty()) {
            alerts.add(new FraudAlert(
                "RAPID_HOP",
                "Rapid fund-layering detected: " + flagged.size() +
                    " account(s) sent 3+ transactions within a 5-minute window",
                new ArrayList<>(flagged)
            ));
        }
        return alerts;
    }
}
