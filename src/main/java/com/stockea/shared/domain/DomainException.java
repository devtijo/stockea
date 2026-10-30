package com.stockea.shared.domain;

public class DomainException extends RuntimeException {
    private final ErrorDominio error;

    public DomainException(ErrorDominio error) {
        super(error.getMensaje());
        this.error = error;
    }

    public ErrorDominio getError() {
        return error;
    }
}