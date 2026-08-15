package com.astrastore.monitoring.exception;

/** The requested service id is not a configured probe target. */
public class UnknownServiceException extends RuntimeException {

    public UnknownServiceException(String serviceId) {
        super("No monitored service with id '" + serviceId + "'.");
    }
}
