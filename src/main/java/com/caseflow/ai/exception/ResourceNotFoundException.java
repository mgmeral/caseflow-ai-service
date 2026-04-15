package com.caseflow.ai.exception;

public class ResourceNotFoundException extends AiServiceException {

    public ResourceNotFoundException(String message) {
        super(message, 404);
    }
}
