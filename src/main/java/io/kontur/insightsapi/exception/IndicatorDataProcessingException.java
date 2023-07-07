package io.kontur.insightsapi.exception;

public class IndicatorDataProcessingException extends RuntimeException {

    public IndicatorDataProcessingException(String message, Throwable err) {
        super(message, err);
    }

    public IndicatorDataProcessingException(String message) {
        super(message);
    }
}