package com.fraudlens.patterns;

import com.fraudlens.graph.TransactionGraph;
import com.fraudlens.model.FraudAlert;

import java.util.List;

/**
 * Strategy Pattern: defines the single contract all fraud-detection algorithms must satisfy.
 *
 * Interface Segregation (SOLID-I): only one method — detect().
 * Dependency Inversion (SOLID-D): FraudAnalysisService depends on this interface,
 * not on any concrete detector class.
 *
 * To add a new fraud rule: create a new class that implements this interface.
 * Zero existing code needs to change (Open/Closed — SOLID-O).
 */
public interface FraudDetector {
    List<FraudAlert> detect(TransactionGraph graph);
}
