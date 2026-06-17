package com.fraudlens.model;
import java.time.LocalDateTime;
 
public class Transaction {
    private String txnId;
    private String fromAccount;
    private String toAccount;
    private double amount;
    private LocalDateTime timestamp;
    private String type; // "NORMAL" or "FRAUD"

    public Transaction(String txnId, String fromAccount, String toAccount,
                       double amount, LocalDateTime timestamp, String type) {
        this.txnId = txnId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getTxnId()        { return txnId; }
    public String getFromAccount()  { return fromAccount; }
    public String getToAccount()    { return toAccount; }
    public double getAmount()       { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getType()         { return type; }
}
