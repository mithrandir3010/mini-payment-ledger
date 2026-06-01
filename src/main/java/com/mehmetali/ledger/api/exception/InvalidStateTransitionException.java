package com.mehmetali.ledger.api.exception;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String from, String to) {
        super("Invalid transition: " + from + " -> " + to);
    }
}
