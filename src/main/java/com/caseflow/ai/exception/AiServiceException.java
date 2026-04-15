package com.caseflow.ai.exception;

import lombok.Getter;

@Getter
public class AiServiceException extends RuntimeException {

    private final int statusCode;

    public AiServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
