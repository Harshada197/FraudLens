package com.fraudlens.data;

/**
 * Defines realistic behaviour profiles for each account range.
 *
 * SOLID-S: single responsibility — profile lookup only.
 *
 * Each profile controls how LifestyleGenerator produces day-to-day
 * transactions for that account:
 *   - txnsPerMonth:   how many lifestyle transactions to generate
 *   - baseAmount:     minimum typical transaction amount (₹)
 *   - amountJitter:   random variation added on top of baseAmount (₹)
 *   - activeHourFrom: earliest hour the account typically transacts
 *   - activeHourTo:   latest hour the account typically transacts
 *   - roundAmounts:   true → round to nearest ₹100 (business behaviour)
 */
public class BehaviourProfiles {

    /** Immutable profile record. */
    public record BehaviourProfile(
            int txnsPerMonth,
            double baseAmount,
            double amountJitter,
            int activeHourFrom,
            int activeHourTo,
            boolean roundAmounts
    ) {}

    // ── Pre-built profiles ──────────────────────────────────────────

    /** Accounts 1–16: cycle-pattern accounts. Low personal activity. */
    private static final BehaviourProfile CYCLE_ACCOUNT =
            new BehaviourProfile(10, 500, 1500, 9, 18, false);

    /** Accounts 17–40: regular users, moderate activity. */
    private static final BehaviourProfile REGULAR_USER =
            new BehaviourProfile(8, 200, 1300, 8, 22, false);

    /** Accounts 41–52: hub/threshold pattern accounts. Business-like. */
    private static final BehaviourProfile BUSINESS_ACCOUNT =
            new BehaviourProfile(6, 1000, 3000, 9, 17, true);

    /** Accounts 53–80: active clean users. Higher volume. */
    private static final BehaviourProfile ACTIVE_USER =
            new BehaviourProfile(15, 100, 4900, 6, 23, false);

    /** Accounts 81–100: merchants / utilities. Very few outgoing txns. */
    private static final BehaviourProfile MERCHANT =
            new BehaviourProfile(3, 1000, 2000, 10, 16, true);

    // ── Lookup ──────────────────────────────────────────────────────

    /**
     * Returns the behaviour profile for a given account number (1–100).
     */
    public static BehaviourProfile forAccount(int accountNum) {
        if (accountNum <= 16) return CYCLE_ACCOUNT;
        if (accountNum <= 40) return REGULAR_USER;
        if (accountNum <= 52) return BUSINESS_ACCOUNT;
        if (accountNum <= 80) return ACTIVE_USER;
        return MERCHANT;
    }
}
