package com.fraudlens.model;
import java.util.List;

// Plain Java class — carries the result of a fraud detection run
public class FraudAlert {
    private String type;        // "CYCLE", "HUB", "RAPID_HOP"
    private String description;
    private List<String> involvedAccounts;
    private String severity;    // "HIGH", "MEDIUM"

    public FraudAlert(String type, String description, List<String> involvedAccounts) {
        this.type = type;
        this.description = description;
        this.involvedAccounts = involvedAccounts;
        this.severity = type.equals("CYCLE") ? "HIGH" : "MEDIUM";
    }

    public String getType()                     { return type; }
    public String getDescription()              { return description; }
    public List<String> getInvolvedAccounts()   { return involvedAccounts; }
    public String getSeverity()                 { return severity; }
}
