package com.fraudlens.data;

import com.fraudlens.model.Account;
import com.fraudlens.model.Transaction;
import com.fraudlens.service.FraudAnalysisService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates startup data seeding with realistic transaction logs.
 *
 * Randomly picks non-consecutive accounts for cycles, hubs, timing chains, and structuring.
 * All transactions are distributed naturally across the January 2024 timeline.
 */
@Component
public class DataSeeder {

    @Autowired
    private FraudAnalysisService fraudAnalysisService;

    @PostConstruct
    public void init() {
        // Step 1: Create 100 accounts using AccountFactory
        List<Account> accounts = new AccountFactory().createAccounts();

        // Seeded random for reproducible fraud participant selection
        Random rnd = new Random(12345L);
        Set<String> usedAccounts = new HashSet<>();

        // Helper to select random distinct non-consecutive peer accounts (1-80)
        // 81-100 are merchants
        String cycle1_1 = pickRandomAccount(rnd, usedAccounts);
        String cycle1_2 = pickRandomAccount(rnd, usedAccounts);
        String cycle1_3 = pickRandomAccount(rnd, usedAccounts);

        String hub1_center = pickRandomAccount(rnd, usedAccounts);

        String rh_1 = pickRandomAccount(rnd, usedAccounts);
        String rh_2 = pickRandomAccount(rnd, usedAccounts);
        String rh_3 = pickRandomAccount(rnd, usedAccounts);
        String rh_4 = pickRandomAccount(rnd, usedAccounts);

        String th_1 = pickRandomAccount(rnd, usedAccounts);

        // Print selected fraud seeds for verification
        System.out.println("[FraudLens Seeding] Selected Fraud Seeds:");
        System.out.println("  Cycle 1: " + cycle1_1 + ", " + cycle1_2 + ", " + cycle1_3);
        System.out.println("  Hub 1 Center: " + hub1_center);
        System.out.println("  Rapid Hop: " + rh_1 + " -> " + rh_2 + " -> " + rh_3 + " -> " + rh_4);
        System.out.println("  Threshold Sender: " + th_1);

        List<Transaction> allTransactions = new ArrayList<>();

        // Generate normal lifestyle transactions for peer accounts
        // Each normal peer account (not in usedAccounts, and <= 80) gets 3 transactions
        LifestyleGenerator lifestyleGen = new LifestyleGenerator();
        BehaviourProfiles.BehaviourProfile regularProfile = new BehaviourProfiles.BehaviourProfile(
                3,      // 3 transactions per month
                200.0,  // base ₹200
                1800.0, // jitter ₹1800 (max ₹2000)
                9,      // active hour from
                21,     // active hour to
                false   // round amounts
        );

        for (Account acc : accounts) {
            int num = Integer.parseInt(acc.getAccountId().substring(4));
            if (num <= 80 && !usedAccounts.contains(acc.getAccountId())) {
                allTransactions.addAll(lifestyleGen.generateFor(acc, regularProfile));
            }
        }

        // Now plant patterns:

        // 1. Cycle 1: C1_1 -> C1_2 -> C1_3 -> C1_1 (Spans multiple days/weeks)
        double c1Amt = 12500.0;
        allTransactions.add(new Transaction(uid("CYC", rnd), cycle1_1, cycle1_2, c1Amt, LocalDateTime.of(2024, 1, 4, 10, 15), "NORMAL"));
        allTransactions.add(new Transaction(uid("CYC", rnd), cycle1_2, cycle1_3, c1Amt, LocalDateTime.of(2024, 1, 10, 14, 30), "NORMAL"));
        allTransactions.add(new Transaction(uid("CYC", rnd), cycle1_3, cycle1_1, c1Amt, LocalDateTime.of(2024, 1, 15, 11, 45), "NORMAL"));
        
        allTransactions.add(new Transaction(uid("CYC", rnd), cycle1_1, cycle1_2, c1Amt, LocalDateTime.of(2024, 1, 19, 9, 30), "NORMAL"));
        allTransactions.add(new Transaction(uid("CYC", rnd), cycle1_2, cycle1_3, c1Amt, LocalDateTime.of(2024, 1, 23, 16, 10), "NORMAL"));
        allTransactions.add(new Transaction(uid("CYC", rnd), cycle1_3, cycle1_1, c1Amt, LocalDateTime.of(2024, 1, 28, 12, 0), "NORMAL"));

        // 2. Hub 1 (Fan-out center H1_C sends to 6 random leaves)
        double h1Amt = 8500.0;
        List<String> h1Leaves = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            h1Leaves.add(pickRandomAccount(rnd, usedAccounts));
        }
        for (int i = 0; i < h1Leaves.size(); i++) {
            String leaf = h1Leaves.get(i);
            allTransactions.add(new Transaction(uid("HUB", rnd), hub1_center, leaf, h1Amt, LocalDateTime.of(2024, 1, 3 + i * 4, 10, 0), "NORMAL"));
            allTransactions.add(new Transaction(uid("HUB", rnd), hub1_center, leaf, h1Amt, LocalDateTime.of(2024, 1, 15 + i * 2, 14, 0), "NORMAL"));
        }

        // 3. Rapid Hop timing-based chain on Jan 18 (4 nodes, 3 hops)
        LocalDateTime rhTime = LocalDateTime.of(2024, 1, 18, 10, 0, 0);
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_1, rh_2, 65000.0, rhTime, "NORMAL"));
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_1, rh_2, 65000.0, rhTime.plusSeconds(8), "NORMAL"));
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_1, rh_2, 65000.0, rhTime.plusSeconds(16), "NORMAL"));

        allTransactions.add(new Transaction(uid("RHP", rnd), rh_2, rh_3, 63500.0, rhTime.plusSeconds(30), "NORMAL"));
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_2, rh_3, 63500.0, rhTime.plusSeconds(38), "NORMAL"));
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_2, rh_3, 63500.0, rhTime.plusSeconds(46), "NORMAL"));

        allTransactions.add(new Transaction(uid("RHP", rnd), rh_3, rh_4, 62000.0, rhTime.plusSeconds(60), "NORMAL"));
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_3, rh_4, 62000.0, rhTime.plusSeconds(68), "NORMAL"));
        allTransactions.add(new Transaction(uid("RHP", rnd), rh_3, rh_4, 62000.0, rhTime.plusSeconds(76), "NORMAL"));

        // 4. Threshold Structuring (evading detection by staying just below ₹49,500)
        int[] thMerchants = {81, 82, 83, 84, 85, 86, 87, 88, 89, 90};
        for (int i = 0; i < 12; i++) {
            double ratio = 0.975 + (i * 0.002);
            double amount1 = Math.round(49500.0 * ratio * 100.0) / 100.0;
            String merchant1 = "ACC_0" + thMerchants[rnd.nextInt(thMerchants.length)];
            LocalDateTime ts1 = LocalDateTime.of(2024, 1, 2 + i * 2, 9, 30);
            allTransactions.add(new Transaction(uid("THR", rnd), th_1, merchant1, amount1, ts1, "NORMAL"));
        }

        // Hand off to analysis service
        fraudAnalysisService.initialize(accounts, allTransactions);
        System.out.println("[FraudLens] Seeded " + accounts.size()
                + " accounts, " + allTransactions.size() + " transactions.");
    }

    private String pickRandomAccount(Random rnd, Set<String> usedSet) {
        while (true) {
            int num = 1 + rnd.nextInt(80);
            String id = "ACC_" + String.format("%03d", num);
            if (!usedSet.contains(id)) {
                boolean consecutive = false;
                for (String used : usedSet) {
                    int usedNum = Integer.parseInt(used.substring(4));
                    if (Math.abs(num - usedNum) <= 1) {
                        consecutive = true;
                        break;
                    }
                }
                if (!consecutive) {
                    usedSet.add(id);
                    return id;
                }
            }
        }
    }

    private String uid(String prefix, Random rnd) {
        return prefix + "_" + String.format("%08X", Math.abs(rnd.nextInt()));
    }
}