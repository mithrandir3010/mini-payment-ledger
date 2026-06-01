package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.api.exception.UnsupportedCurrencyPairException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class FxService {

    private static final Map<String, BigDecimal> RATES = Map.of(
        "USD_EUR", new BigDecimal("0.920000"),
        "EUR_USD", new BigDecimal("1.087000"),
        "USD_TRY", new BigDecimal("32.500000"),
        "TRY_USD", new BigDecimal("0.030800"),
        "EUR_TRY", new BigDecimal("35.300000"),
        "TRY_EUR", new BigDecimal("0.028300"),
        "USD_GBP", new BigDecimal("0.790000"),
        "GBP_USD", new BigDecimal("1.266000"),
        "EUR_GBP", new BigDecimal("0.860000"),
        "GBP_EUR", new BigDecimal("1.163000")
    );

    public BigDecimal getRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        String pair = fromCurrency + "_" + toCurrency;
        BigDecimal rate = RATES.get(pair);
        if (rate == null) {
            throw new UnsupportedCurrencyPairException(fromCurrency, toCurrency);
        }
        return rate;
    }
}
