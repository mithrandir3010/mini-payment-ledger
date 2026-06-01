package com.mehmetali.ledger.api.exception;

public class UnsupportedCurrencyPairException extends RuntimeException {

    public UnsupportedCurrencyPairException(String from, String to) {
        super("Unsupported currency pair: " + from + "/" + to);
    }
}
