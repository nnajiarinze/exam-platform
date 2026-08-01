package se.medbo.examplatform.ai.provider;

import java.util.Map;

public final class AiProviderException extends RuntimeException {
    private final String code; private final boolean transientFailure;private final Map<String,Object> diagnostics;private final String rawResponse;
    public AiProviderException(String code,boolean transientFailure,String message){this(code,transientFailure,message,Map.of(),null);}
    public AiProviderException(String code,boolean transientFailure,String message,Map<String,Object> diagnostics,String rawResponse){super(message);this.code=code;this.transientFailure=transientFailure;this.diagnostics=diagnostics==null?Map.of():Map.copyOf(diagnostics);this.rawResponse=rawResponse;}
    public String code(){return code;} public boolean transientFailure(){return transientFailure;}
    public Map<String,Object> diagnostics(){return diagnostics;}public String rawResponse(){return rawResponse;}
}
