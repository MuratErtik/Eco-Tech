package org.murat.samsungichackathon.exceptions;

public class AiProviderException extends RuntimeException {
    public AiProviderException(String message, Exception e) {
        super(message);
    }
}
