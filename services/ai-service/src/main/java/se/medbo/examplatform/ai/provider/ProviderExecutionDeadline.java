package se.medbo.examplatform.ai.provider;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Absolute provider lifecycle deadline independent of HTTP-client timeout behavior. */
@Component
final class ProviderExecutionDeadline {
  private final ThreadPoolExecutor workers;
  private final Duration deadline;
  private final String processInstanceId;
  ProviderExecutionDeadline(@Value("${ai.provider.hard-deadline-seconds:45}")long seconds,
      @Value("${ai.provider.execution-workers:2}")int workerCount){
    int count=Math.max(1,Math.min(workerCount,4));var sequence=new AtomicInteger();
    this.deadline=Duration.ofSeconds(Math.max(1,seconds));
    this.processInstanceId=java.lang.management.ManagementFactory.getRuntimeMXBean().getName()+"-"+java.util.UUID.randomUUID();
    this.workers=new ThreadPoolExecutor(count,count,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(count),r->{
      var thread=new Thread(r,"provider-execution-"+sequence.incrementAndGet());thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
  }
  StructuredAiProvider.Response execute(StructuredAiProvider provider,StructuredAiProvider.Request request){
    long started=System.nanoTime();Future<StructuredAiProvider.Response> future=workers.submit(()->provider.execute(request));
    try{return future.get(deadline.toMillis(),TimeUnit.MILLISECONDS);}
    catch(TimeoutException e){boolean cancelled=future.cancel(true);long elapsed=(System.nanoTime()-started)/1_000_000;
      throw new AiProviderException("AI_PROVIDER_HARD_TIMEOUT",false,"Provider execution exceeded its absolute deadline",
          Map.of("configuredDeadlineMs",deadline.toMillis(),"elapsedMs",elapsed,"cancellationSucceeded",cancelled,
              "processInstanceId",processInstanceId,"outcomeCertainty","OUTCOME_UNKNOWN"),null);}
    catch(InterruptedException e){Thread.currentThread().interrupt();future.cancel(true);throw new AiProviderException("AI_REQUEST_CANCELLED",false,"Provider execution was cancelled");}
    catch(java.util.concurrent.ExecutionException e){if(e.getCause() instanceof AiProviderException providerError)throw providerError;throw new AiProviderException("AI_PROVIDER_RESPONSE_INVALID",false,"Provider execution failed");}
  }
  long deadlineMillis(){return deadline.toMillis();}String processInstanceId(){return processInstanceId;}
  int activeWorkers(){return workers.getActiveCount();}
  @PreDestroy void close(){workers.shutdownNow();}
}
