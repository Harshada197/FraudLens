package com.fraudlens.data;

import com.fraudlens.model.Account;
import com.fraudlens.model.Transaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * SOLID-S: single responsibility — generates realistic day-to-day transactions
 * for one account across January 2024.
 *
 * Rules:
 * - All transactions go FROM this account TO a counterparty (outgoing only),
 * keeping lifestyle data directionally simple and avoiding accidental cycles.
 * - All transactions carry type "NORMAL" — risk is FraudAnalysisService's job.
 */
public class LifestyleGenerator {

    private static final LocalDateTime JAN_START = LocalDateTime.of(2024, 1, 1, 0, 0);

    public List<Transaction> generateFor(Account account,
            BehaviourProfiles.BehaviourProfile profile) {
        List<Transaction> txns = new ArrayList<>();
        int num = Integer.parseInt(account.getAccountId().substring(4));
        Random rnd = new Random(num * 17L + 31L);

        for (int i = 0; i < profile.txnsPerMonth(); i++) {
            LocalDateTime ts = randomTimestamp(profile, rnd);
            double amount = randomAmount(profile, rnd);
            String to = pickCounterparty(num, rnd);
            txns.add(new Transaction(uid(rnd), account.getAccountId(), to, amount, ts, null));
        }
        return txns;
    }

    private LocalDateTime randomTimestamp(BehaviourProfiles.BehaviourProfile p, Random rnd) {
        int day = rnd.nextInt(31);
        int span = Math.max(1, p.activeHourTo() - p.activeHourFrom());
        int hour = (p.activeHourFrom() + rnd.nextInt(span)) % 24;
        int min = rnd.nextInt(60);
        return JAN_START.plusDays(day).withHour(hour).withMinute(min);
    }

    private double randomAmount(BehaviourProfiles.BehaviourProfile p, Random rnd) {
        double amt = p.baseAmount() + rnd.nextDouble() * p.amountJitter();
        return p.roundAmounts() ? Math.round(amt / 100.0) * 100.0 : Math.round(amt * 100.0) / 100.0;
    }

    /**
     * Picks a counterparty.
     * Senders > 80 (clean users) send back to business/peer accounts (1-60).
     * Senders <= 80: 70% chance to send to clean pool (81-100) and 30% chance to
     * send to a peer account (1-80).
     */
    private String pickCounterparty(int senderNum, Random rnd) {
        int peer;
        if (senderNum > 80) {
            // clean users send back to business/peer accounts
            do {
                peer = 1 + rnd.nextInt(60);
            } while (peer == senderNum);
        } else {
            // 70% chance: send to clean pool (081-100) — merchant/utility payments
            // 30% chance: send to a peer account (001-080) — person-to-person transfers
            boolean sendToClean = rnd.nextInt(10) < 7;
            do {
                peer = sendToClean
                        ? 81 + rnd.nextInt(20) // clean pool
                        : 1 + rnd.nextInt(80); // peer-to-peer
            } while (peer == senderNum);
        }
        return "ACC_" + String.format("%03d", peer);
    }

    private static String uid(Random rnd) {
        return "LST_" + String.format("%08X", Math.abs(rnd.nextInt()));
    }
}
