package com.reviewflow.infrastructure.jobs;

import com.reviewflow.infrastructure.monitoring.ReviewFlowMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Slf4j
public class AsyncJobConfig {

  @Bean(name = "csvWorkerExecutor")
  public Executor csvWorkerExecutor(
      @Value("${async.csv.core-pool-size:2}") int core,
      @Value("${async.csv.max-pool-size:5}") int max,
      @Value("${async.csv.queue-capacity:20}") int queue) {
    return buildExecutor(core, max, queue, "csv-worker-", new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean(name = "uploadExecutor")
  public Executor uploadExecutor(
      @Value("${async.upload.core-pool-size:3}") int core,
      @Value("${async.upload.max-pool-size:10}") int max,
      @Value("${async.upload.queue-capacity:50}") int queue) {
    return buildExecutor(core, max, queue, "file-upload-", new ThreadPoolExecutor.AbortPolicy());
  }

  // pdfExecutor: intentionally small (max 3). PDF generation is CPU-bound
  // via OpenPDF; larger pool on t3.micro would cause GC pressure under load.
  @Bean(name = "pdfExecutor")
  public Executor pdfExecutor(
      @Value("${async.pdf.core-pool-size:1}") int core,
      @Value("${async.pdf.max-pool-size:3}") int max,
      @Value("${async.pdf.queue-capacity:20}") int queue) {
    return buildExecutor(core, max, queue, "pdf-gen-", new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean(name = "gradeAggregateExecutor")
  public Executor gradeAggregateExecutor(
      @Value("${async.grade.core-pool-size:2}") int core,
      @Value("${async.grade.max-pool-size:5}") int max,
      @Value("${async.grade.queue-capacity:100}") int queue,
      ReviewFlowMetrics metrics) {

    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(core);
    exec.setMaxPoolSize(max);
    exec.setQueueCapacity(queue);
    exec.setThreadNamePrefix("grade-agg-");
    exec.setWaitForTasksToCompleteOnShutdown(true);
    exec.setAwaitTerminationSeconds(30);

    exec.setRejectedExecutionHandler(
        (runnable, executor) -> {
          if (!executor.getQueue().isEmpty()) {
            executor.getQueue().poll();
          }
          try {
            executor.execute(runnable);
          } catch (RejectedExecutionException e) {
            log.warn(
                "gradeAggregateExecutor: task dropped after discard-oldest. Queue depth: {}",
                executor.getQueue().size());
          }
          metrics.recordAsyncRejected("gradeAggregateExecutor");
        });

    exec.initialize();
    return exec;
  }

  @Bean(name = "scanExecutor")
  public Executor scanExecutor(
      @Value("${async.scan.core-pool-size:1}") int core,
      @Value("${async.scan.max-pool-size:2}") int max,
      @Value("${async.scan.queue-capacity:10}") int queue,
      ReviewFlowMetrics metrics) {

    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(core);
    exec.setMaxPoolSize(max);
    exec.setQueueCapacity(queue);
    exec.setThreadNamePrefix("clamav-scan-");
    exec.setWaitForTasksToCompleteOnShutdown(true);
    exec.setAwaitTerminationSeconds(60);

    exec.setRejectedExecutionHandler(
        (runnable, executor) -> {
          metrics.recordAsyncRejected("scanExecutor");
          metrics.recordScanRejected();
          log.warn(
              "scanExecutor queue full — ClamAV scan rejected. Queue depth: {}",
              executor.getQueue().size());
          throw new RejectedExecutionException("ClamAV scan queue full");
        });

    exec.initialize();
    return exec;
  }

  private static ThreadPoolTaskExecutor buildExecutor(
      int core,
      int max,
      int queue,
      String prefix,
      RejectedExecutionHandler rejection) {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
    exec.setCorePoolSize(core);
    exec.setMaxPoolSize(max);
    exec.setQueueCapacity(queue);
    exec.setThreadNamePrefix(prefix);
    exec.setRejectedExecutionHandler(rejection);
    exec.setWaitForTasksToCompleteOnShutdown(true);
    exec.setAwaitTerminationSeconds(30);
    exec.initialize();
    return exec;
  }
}
