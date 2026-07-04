package com.bankanalyzer.validation;

/**
 * Validates a target object, throwing {@link IllegalArgumentException} when it fails a rule.
 * {@link com.bankanalyzer.api.GlobalExceptionHandler} maps that exception to HTTP 400.
 */
public interface Validator<T> {

    void validate(T target);
}
