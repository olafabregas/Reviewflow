package com.reviewflow.infrastructure.jobs;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
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
      @Value("${async.grade.queue-capacity:100}") int queue) {
    return buildExecutor(
        core, max, queue, "grade-agg-", new ThreadPoolExecutor.DiscardOldestPolicy());
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
