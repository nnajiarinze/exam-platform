package se.medbo.examplatform.ai.provider;

public final class AiProviderException extends RuntimeException {
    private final String code; private final boolean transientFailure;
    public AiProviderException(String code,boolean transientFailure,String message){super(message);this.code=code;this.transientFailure=transientFailure;}
    public String code(){return code;} public boolean transientFailure(){return transientFailure;}
}
