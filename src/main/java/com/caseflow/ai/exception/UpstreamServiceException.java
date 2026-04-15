package com.caseflow.ai.exception;

public class UpstreamServiceException extends AiServiceException {

    public UpstreamServiceException(String message) {
        super(message, 502);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, 502, cause);
    }
}
