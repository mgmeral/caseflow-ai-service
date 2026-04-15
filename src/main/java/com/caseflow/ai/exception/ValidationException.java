package com.caseflow.ai.exception;

public class ValidationException extends AiServiceException {

    public ValidationException(String message) {
        super(message, 400);
    }
}
