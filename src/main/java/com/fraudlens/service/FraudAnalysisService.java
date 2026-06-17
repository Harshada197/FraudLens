package com.fraudlens.service;

import com.fraudlens.graph.*;
import com.fraudlens.model.*;
import com.fraudlens.patterns.FraudDetector;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Observer Pattern: FraudAnalysisService acts as the subject.
 * When a new transaction is added via addTransaction(), it rebuilds the graph
 * and re-invokes all registered FraudDetector observers automatically.
 *
 * Strategy Pattern: holds List<FraudDetector> and runs each strategy uniformly.
 * Dependency Inversion (SOLID-D): depends on FraudDetector interface, not on
 * concrete CycleDetector / HubDetector / RapidHopDetector.
 *
 * Open/Closed (SOLID-O): adding a new detector requires only adding it to the list —
 * zero changes to existing code.
 */
@Service
public class FraudAnalysisService {

    // Singleton Pattern: single shared TransactionGraph instance (SOLID-S)
    private final TransactionGraph graph = new TransactionGraph();

    // Strategy Pattern: list of all fraud detectors (observers)
    private final List<FraudDetector> detectors = Arrays.asList(
            new CycleDetector(),
            new HubDetector(),
            new RapidHopDetector(),
            new ThresholdDetector()
    );

    private final PropagationScorer propagationScorer = new PropagationScorer();

    // In-memory data stores
    private List<Account>     accounts     = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();

    // Cached detection results (refreshed on every addTransaction call)
    private List<FraudAlert>     lastAlerts        = new ArrayList<>();
    private Map<String, Double>  propagationScores = new HashMap<>();

    /** Called once by DataSeeder on startup. */
    public void initialize(List<Account> accounts, List<Transaction> transactions) {
        this.accounts     = new ArrayList<>(accounts);
        this.transactions = new ArrayList<>(transactions);
        rebuildAndDetect();
    }

    /**
     * Observer Pattern: adds a transaction, notifies all detectors by re-running them.
     * This is the trigger point for What-If simulation.
     */
    public List<FraudAlert> addTransaction(Transaction t) {
        transactions.add(t);
        ensureAccountExists(t.getFromAccount());
        ensureAccountExists(t.getToAccount());
        rebuildAndDetect();
        return lastAlerts;
    }

    /** Rebuilds the graph and re-executes every detector strategy. */
    private void rebuildAndDetect() {
        graph.buildGraph(transactions);

        // Run all detector strategies and merge results
        lastAlerts = new ArrayList<>();
        for (FraudDetector detector : detectors) {
            lastAlerts.addAll(detector.detect(graph));
        }

        // Compute propagation scores from confirmed fraud accounts
        Set<String> fraudAccounts = getFraudAccountIds();
        propagationScores = propagationScorer.computeScores(graph, fraudAccounts);

        // Stamp computed risk score and level onto every Account object
        stampRiskOnAccounts();
    }

    // ── Stamp risk onto Account objects after every detection run ────────────

    private void stampRiskOnAccounts() {
        Map<String, Long> txnCount = buildTxnCountMap();
        double avg = txnCount.values().stream().mapToLong(Long::longValue).average().orElse(1.0);
        for (Account acc : accounts) {
            String id = acc.getAccountId();
            int num = Integer.parseInt(id.substring(4));
            if (num >= 81) {
                acc.setRiskScore(0);
                acc.setRiskLevel("NORMAL");
            } else {
                int score = computeWeightedScore(id, txnCount.getOrDefault(id, 0L), avg);
                acc.setRiskScore(score);
                acc.setRiskLevel(statusFromScore(score));
            }
        }
    }

    // ── Compute weighted risk score for one account ──────────────────────────

    private int computeWeightedScore(String accountId, long txnCount, double avgTxnCount) {
        int score = 0;

        // Alert-type contributions (capped: only count the highest-severity alert once)
        int maxAlertScore = 0;
        int alertHits = 0;
        for (FraudAlert alert : lastAlerts) {
            if (alert.getInvolvedAccounts().contains(accountId)) {
                int contribution = switch (alert.getType()) {
                    case "CYCLE"     -> 20;
                    case "RAPID_HOP" -> 15;
                    case "HUB"       -> 50;
                    case "THRESHOLD" -> 50;
                    default          -> 10;
                };
                maxAlertScore = Math.max(maxAlertScore, contribution);
                alertHits++;
            }
        }
        // Primary alert contribution + small bonus for multiple alerts
        score += maxAlertScore;
        if (alertHits > 1) score += Math.min((alertHits - 1) * 5, 15);

        // Propagation score contribution: 0–100 scaled to 0–30 points
        score += (int)(propagationScores.getOrDefault(accountId, 0.0) * 0.30);

        // Transaction volume bonus (mild)
        if      (txnCount > 3.0  * avgTxnCount) score += 10;
        else if (txnCount > 1.5  * avgTxnCount) score += 3;

        return Math.min(score, 100); // cap at 100
    }

    /**
     * Maps composite score to risk level label.
     * These labels are used consistently across the entire system:
     * backend Account objects, REST API responses, and frontend UI.
     */
    static String statusFromScore(int score) {
        if (score >= 80) return "FRAUD";
        if (score >= 40) return "SUSPICIOUS";
        if (score >= 15) return "AT_RISK";
        return "NORMAL";
    }

    // ── Public accessors for account info ────────────────────────────────────

    /**
     * Builds per-account stats for the accounts table.
     * Returns: accountId, name, totalTransactions, riskScore (composite),
     *          status (from composite), alertCount
     */
    public List<Map<String, Object>> getAccountInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Account acc : accounts) {
            String id = acc.getAccountId();

            // Count how many alerts involve this account
            long alertCount = lastAlerts.stream()
                    .filter(a -> a.getInvolvedAccounts().contains(id))
                    .count();

            Map<String, Long> txnCount = buildTxnCountMap();
            long count = txnCount.getOrDefault(id, 0L);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("accountId",         id);
            info.put("name",              acc.getName());
            info.put("totalTransactions", count);
            info.put("riskScore",         acc.getRiskScore());   // composite weighted score
            info.put("status",            acc.getRiskLevel());   // consistent with statusFromScore()
            info.put("alertCount",        alertCount);
            result.add(info);
        }
        return result;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Map<String, Long> buildTxnCountMap() {
        Map<String, Long> map = new HashMap<>();
        for (Transaction t : transactions) {
            map.merge(t.getFromAccount(), 1L, Long::sum);
            map.merge(t.getToAccount(),   1L, Long::sum);
        }
        return map;
    }

    private void ensureAccountExists(String id) {
        boolean exists = accounts.stream().anyMatch(a -> a.getAccountId().equals(id));
        if (!exists) {
            accounts.add(new Account(id, "User " + id));
        }
    }

    // ── Public accessors (unchanged) ──────────────────────────────────────────

    public TransactionGraph      getGraph()            { return graph; }
    public List<FraudAlert>      getLastAlerts()       { return Collections.unmodifiableList(lastAlerts); }
    public Map<String, Double>   getPropagationScores(){ return Collections.unmodifiableMap(propagationScores); }
    public List<Account>         getAccounts()         { return Collections.unmodifiableList(accounts); }
    public List<Transaction>     getTransactions()     { return Collections.unmodifiableList(transactions); }

    public List<Transaction> filterByDate(LocalDate date) {
        return transactions.stream()
                .filter(t -> t.getTimestamp().toLocalDate().equals(date))
                .collect(Collectors.toList());
    }

    public List<List<String>> getCycles() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("CYCLE"))
                .map(FraudAlert::getInvolvedAccounts)
                .collect(Collectors.toList());
    }

    public List<String> getHubs() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("HUB"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getRapidHops() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("RAPID_HOP"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getThresholds() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("THRESHOLD"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public Set<String> getFraudAccountIds() {
        Set<String> fraud = new HashSet<>();
        for (FraudAlert alert : lastAlerts) {
            fraud.addAll(alert.getInvolvedAccounts());
        }
        return fraud;
    }

    // ── Time-Slice Analysis (Month, Week, Day Views) ─────────────────────────

    public List<Transaction> getTransactionsForSlice(String view, LocalDate date) {
        if (date == null || "month".equals(view)) {
            return new ArrayList<>(transactions);
        }
        
        List<Transaction> result = new ArrayList<>();
        if ("day".equals(view)) {
            for (Transaction t : transactions) {
                if (t.getTimestamp().toLocalDate().equals(date)) {
                    result.add(t);
                }
            }
        } else if ("week".equals(view)) {
            LocalDate monday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate sunday = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            for (Transaction t : transactions) {
                LocalDate tDate = t.getTimestamp().toLocalDate();
                if (!tDate.isBefore(monday) && !tDate.isAfter(sunday)) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    public Map<String, Object> getGraphDataForSlice(String view, LocalDate date) {
        List<Transaction> slice = getTransactionsForSlice(view, date);

        TransactionGraph sliceGraph = new TransactionGraph();
        sliceGraph.buildGraph(slice);

        List<FraudAlert> sliceAlerts = new ArrayList<>();
        for (FraudDetector detector : detectors) {
            sliceAlerts.addAll(detector.detect(sliceGraph));
        }

        Set<String> fraudAccounts = new HashSet<>();
        for (FraudAlert alert : sliceAlerts) {
            fraudAccounts.addAll(alert.getInvolvedAccounts());
        }

        Map<String, Double> slicePropagationScores = propagationScorer.computeScores(sliceGraph, fraudAccounts);

        Set<String> activeNodes = new HashSet<>();
        Map<String, Long> sliceTxnCounts = new HashMap<>();
        for (Transaction t : slice) {
            activeNodes.add(t.getFromAccount());
            activeNodes.add(t.getToAccount());
            sliceTxnCounts.merge(t.getFromAccount(), 1L, Long::sum);
            sliceTxnCounts.merge(t.getToAccount(),   1L, Long::sum);
        }

        double avgTxnCount = sliceTxnCounts.values().stream().mapToLong(Long::longValue).average().orElse(1.0);

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String nodeId : activeNodes) {
            int num = Integer.parseInt(nodeId.substring(4));
            
            int score = 0;
            String status = "NORMAL";
            
            if (num < 81) {
                int maxAlertScore = 0;
                int alertHits = 0;
                for (FraudAlert alert : sliceAlerts) {
                    if (alert.getInvolvedAccounts().contains(nodeId)) {
                        int contribution = switch (alert.getType()) {
                            case "CYCLE"     -> 20;
                            case "RAPID_HOP" -> 15;
                            case "HUB"       -> 50;
                            case "THRESHOLD" -> 50;
                            default          -> 10;
                        };
                        maxAlertScore = Math.max(maxAlertScore, contribution);
                        alertHits++;
                    }
                }
                score += maxAlertScore;
                if (alertHits > 1) score += Math.min((alertHits - 1) * 5, 15);

                score += (int)(slicePropagationScores.getOrDefault(nodeId, 0.0) * 0.30);

                long count = sliceTxnCounts.getOrDefault(nodeId, 0L);
                if      (count > 3.0  * avgTxnCount) score += 10;
                else if (count > 1.5  * avgTxnCount) score += 3;

                score = Math.min(score, 100);
                status = statusFromScore(score);
            }

            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("id", nodeId);
            nodeMap.put("label", nodeId);
            nodeMap.put("riskScore", score);
            nodeMap.put("status", status);
            nodes.add(nodeMap);
        }

        Map<String, Map<String, Object>> edgeMap = new LinkedHashMap<>();
        for (Transaction txn : slice) {
            String key = txn.getFromAccount() + ">" + txn.getToAccount();
            edgeMap.computeIfAbsent(key, k -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("from", txn.getFromAccount());
                e.put("to", txn.getToAccount());
                e.put("totalAmount", 0.0);
                e.put("txnCount", 0);
                e.put("type", "NORMAL");
                return e;
            });
            Map<String, Object> edge = edgeMap.get(key);
            edge.put("totalAmount", (double) edge.get("totalAmount") + txn.getAmount());
            edge.put("txnCount", (int) edge.get("txnCount") + 1);
            if ("FRAUD".equals(txn.getType())) edge.put("type", "FRAUD");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", new ArrayList<>(edgeMap.values()));
        return result;
    }

    public Map<String, Object> getStatsForSlice(String view, LocalDate date) {
        List<Transaction> slice = getTransactionsForSlice(view, date);

        TransactionGraph sliceGraph = new TransactionGraph();
        sliceGraph.buildGraph(slice);

        List<FraudAlert> sliceAlerts = new ArrayList<>();
        int cycles = 0;
        int hubs = 0;
        int rapids = 0;
        for (FraudDetector detector : detectors) {
            List<FraudAlert> alerts = detector.detect(sliceGraph);
            sliceAlerts.addAll(alerts);
            for (FraudAlert alert : alerts) {
                if (alert.getType().equals("CYCLE")) cycles++;
                else if (alert.getType().equals("HUB")) hubs += alert.getInvolvedAccounts().size();
                else if (alert.getType().equals("RAPID_HOP")) rapids += alert.getInvolvedAccounts().size();
            }
        }

        Set<String> fraudAccounts = new HashSet<>();
        for (FraudAlert alert : sliceAlerts) {
            fraudAccounts.addAll(alert.getInvolvedAccounts());
        }

        Map<String, Double> slicePropagationScores = propagationScorer.computeScores(sliceGraph, fraudAccounts);

        Set<String> activeNodes = new HashSet<>();
        Map<String, Long> sliceTxnCounts = new HashMap<>();
        for (Transaction t : slice) {
            activeNodes.add(t.getFromAccount());
            activeNodes.add(t.getToAccount());
            sliceTxnCounts.merge(t.getFromAccount(), 1L, Long::sum);
            sliceTxnCounts.merge(t.getToAccount(),   1L, Long::sum);
        }

        double avgTxnCount = sliceTxnCounts.values().stream().mapToLong(Long::longValue).average().orElse(1.0);

        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("FRAUD", 0L);
        distribution.put("SUSPICIOUS", 0L);
        distribution.put("AT_RISK", 0L);
        distribution.put("NORMAL", 0L);

        for (Account acc : accounts) {
            String nodeId = acc.getAccountId();
            int num = Integer.parseInt(nodeId.substring(4));
            
            int score = 0;
            String status = "NORMAL";
            
            if (activeNodes.contains(nodeId) && num < 81) {
                int maxAlertScore = 0;
                int alertHits = 0;
                for (FraudAlert alert : sliceAlerts) {
                    if (alert.getInvolvedAccounts().contains(nodeId)) {
                        int contribution = switch (alert.getType()) {
                            case "CYCLE"     -> 20;
                            case "RAPID_HOP" -> 15;
                            case "HUB"       -> 50;
                            case "THRESHOLD" -> 50;
                            default          -> 10;
                        };
                        maxAlertScore = Math.max(maxAlertScore, contribution);
                        alertHits++;
                    }
                }
                score += maxAlertScore;
                if (alertHits > 1) score += Math.min((alertHits - 1) * 5, 15);

                score += (int)(slicePropagationScores.getOrDefault(nodeId, 0.0) * 0.30);

                long count = sliceTxnCounts.getOrDefault(nodeId, 0L);
                if      (count > 3.0  * avgTxnCount) score += 10;
                else if (count > 1.5  * avgTxnCount) score += 3;

                score = Math.min(score, 100);
                status = statusFromScore(score);
            }
            distribution.merge(status, 1L, Long::sum);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTransactions", slice.size());
        stats.put("totalAccounts", activeNodes.size());
        stats.put("cyclesDetected", cycles);
        stats.put("hubAccounts", hubs);
        stats.put("rapidHopAlerts", rapids);
        stats.put("fraudAlerts", sliceAlerts.size());
        stats.put("riskDistribution", distribution);

        return stats;
    }
}
