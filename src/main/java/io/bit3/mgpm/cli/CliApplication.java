package io.bit3.mgpm.cli;

import io.bit3.mgpm.cmd.Args;
import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.RepositoryConfig;
import io.bit3.mgpm.worker.Activity;
import io.bit3.mgpm.worker.Worker;
import io.bit3.mgpm.worker.WorkerObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CliApplication {
  private final Logger logger;
  private final Args args;
  private final Config config;
  private final AnsiOutput output;
  private final int padding;
  private final String printPattern;
  private final WorkerObserver observer;

  public CliApplication(Args args, Config config) {
    logger = LoggerFactory.getLogger(CliApplication.class);
    this.args = args;
    this.config = config;
    output = new AnsiOutput();
    padding = config.getRepositories()
        .stream()
        .map(r -> r.getName().length())
        .max(Integer::max)
        .get();
    printPattern = "%" + padding + "s";
    observer = new CliWorkerObserver();
  }

  public void run() {
    int cores = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(2 * cores);

    for (final RepositoryConfig repositoryConfig : config.getRepositories()) {
      Worker worker = new Worker(config, repositoryConfig, args.isDoInit(), args.isDoUpdate());
      worker.registerObserver(observer);
      executor.submit(worker);
    }
    executor.shutdown();
  }

  private class CliWorkerObserver implements WorkerObserver {
    @Override
    public void start(Worker worker) {
    }

    @Override
    public void activity(Activity activity, Worker worker) {
      synchronized (output) {
        output
            .print(printPattern, worker.getRepositoryConfig().getName())
            .print(Color.YELLOW, " [%s] ", activity.getAction().toString())
            .print(activity.getMessage())
            .println();
      }
    }

    @Override
    public void end(Worker worker) {

    }
  }
}
