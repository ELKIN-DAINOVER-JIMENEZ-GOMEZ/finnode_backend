package com.finnode.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades para la detección de fraude.
 */
@ConfigurationProperties(prefix = "fraud")
public class FraudProperties {

    /** Umbral (0.0 - 1.0) por encima del cual la transacción se considera de alto riesgo. */
    private double riskThreshold = 0.75;

    public double getRiskThreshold() {
        return riskThreshold;
    }

    public void setRiskThreshold(double riskThreshold) {
        this.riskThreshold = riskThreshold;
    }
}

