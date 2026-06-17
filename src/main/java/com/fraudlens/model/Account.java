package com.fraudlens.model;

// Plain Java class — no JPA, no annotations
public class Account {
    private final String accountId;
    private final String name;

    // Risk fields — ONLY ever set by FraudAnalysisService, never by the data layer
    private String riskLevel = "UNSCORED";
    private int    riskScore = 0;

    public Account(String accountId, String name) {
        this.accountId = accountId;
        this.name      = name;
    }

    public String getAccountId() { return accountId; }
    public String getName()      { return name; }
    public String getRiskLevel() { return riskLevel; }
    public int    getRiskScore() { return riskScore; }

    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setRiskScore(int riskScore)    { this.riskScore = riskScore; }
}
