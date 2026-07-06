package com.example.subsystemdiscovery.boundaryanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunable thresholds for boundary-node detection.
 *
 * <p>The coupling defaults intentionally match the subsystem discovery
 * inter-cluster thresholds: HIGH when weight >= 20 or edge count >= 10, MEDIUM
 * when weight >= 8 or edge count >= 4.
 */
@Component
@ConfigurationProperties(prefix = "boundary.analysis")
public class BoundaryAnalysisProperties {

    private int minCrossEdges = 1;
    private double minCrossWeight = 0.0;
    private double minBoundaryScore = 0.0;

    private double mediumCouplingWeightThreshold = 8.0;
    private int mediumCouplingEdgeThreshold = 4;
    private double highCouplingWeightThreshold = 20.0;
    private int highCouplingEdgeThreshold = 10;

    private double mediumRiskImportanceScore = 0.45;
    private double highRiskImportanceScore = 0.75;

    private int defaultTopNodeLimit = 25;
    private int defaultHotspotLimit = 20;
    private int maxTopLimit = 250;
    private int pairTopNodeLimit = 5;
    private int summaryMapLimit = 20;

    public int getMinCrossEdges() {
        return minCrossEdges;
    }

    public void setMinCrossEdges(int minCrossEdges) {
        this.minCrossEdges = minCrossEdges;
    }

    public double getMinCrossWeight() {
        return minCrossWeight;
    }

    public void setMinCrossWeight(double minCrossWeight) {
        this.minCrossWeight = minCrossWeight;
    }

    public double getMinBoundaryScore() {
        return minBoundaryScore;
    }

    public void setMinBoundaryScore(double minBoundaryScore) {
        this.minBoundaryScore = minBoundaryScore;
    }

    public double getMediumCouplingWeightThreshold() {
        return mediumCouplingWeightThreshold;
    }

    public void setMediumCouplingWeightThreshold(double mediumCouplingWeightThreshold) {
        this.mediumCouplingWeightThreshold = mediumCouplingWeightThreshold;
    }

    public int getMediumCouplingEdgeThreshold() {
        return mediumCouplingEdgeThreshold;
    }

    public void setMediumCouplingEdgeThreshold(int mediumCouplingEdgeThreshold) {
        this.mediumCouplingEdgeThreshold = mediumCouplingEdgeThreshold;
    }

    public double getHighCouplingWeightThreshold() {
        return highCouplingWeightThreshold;
    }

    public void setHighCouplingWeightThreshold(double highCouplingWeightThreshold) {
        this.highCouplingWeightThreshold = highCouplingWeightThreshold;
    }

    public int getHighCouplingEdgeThreshold() {
        return highCouplingEdgeThreshold;
    }

    public void setHighCouplingEdgeThreshold(int highCouplingEdgeThreshold) {
        this.highCouplingEdgeThreshold = highCouplingEdgeThreshold;
    }

    public double getMediumRiskImportanceScore() {
        return mediumRiskImportanceScore;
    }

    public void setMediumRiskImportanceScore(double mediumRiskImportanceScore) {
        this.mediumRiskImportanceScore = mediumRiskImportanceScore;
    }

    public double getHighRiskImportanceScore() {
        return highRiskImportanceScore;
    }

    public void setHighRiskImportanceScore(double highRiskImportanceScore) {
        this.highRiskImportanceScore = highRiskImportanceScore;
    }

    public int getDefaultTopNodeLimit() {
        return defaultTopNodeLimit;
    }

    public void setDefaultTopNodeLimit(int defaultTopNodeLimit) {
        this.defaultTopNodeLimit = defaultTopNodeLimit;
    }

    public int getDefaultHotspotLimit() {
        return defaultHotspotLimit;
    }

    public void setDefaultHotspotLimit(int defaultHotspotLimit) {
        this.defaultHotspotLimit = defaultHotspotLimit;
    }

    public int getMaxTopLimit() {
        return maxTopLimit;
    }

    public void setMaxTopLimit(int maxTopLimit) {
        this.maxTopLimit = maxTopLimit;
    }

    public int getPairTopNodeLimit() {
        return pairTopNodeLimit;
    }

    public void setPairTopNodeLimit(int pairTopNodeLimit) {
        this.pairTopNodeLimit = pairTopNodeLimit;
    }

    public int getSummaryMapLimit() {
        return summaryMapLimit;
    }

    public void setSummaryMapLimit(int summaryMapLimit) {
        this.summaryMapLimit = summaryMapLimit;
    }
}
