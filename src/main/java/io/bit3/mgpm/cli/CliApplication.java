package io.bit3.mgpm.cli;

import io.bit3.mgpm.cmd.Args;
import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.RepositoryConfig;
import io.bit3.mgpm.worker.AbstractWorkerObserver;
import io.bit3.mgpm.worker.FromToIsh;
import io.bit3.mgpm.worker.LoggingWorkerObserver;
import io.bit3.mgpm.worker.Update;
import io.bit3.mgpm.worker.Worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CliApplication {
  private final Logger logger = LoggerFactory.getLogger(CliApplication.class);
  private final Args args;
  private final Config config;
  private final AnsiOutput output;
  private final int repositoryPadding;

  public CliApplication(Args args, Config config) {
    this.args = args;
    this.config = config;
    this.output = AnsiOutput.getInstance();
    this.repositoryPadding = config.getRepositories()
        .stream()
        .mapToInt(r -> r.getName().length())
        .max()
        .getAsInt();
  }

  public void run() {
    int cores = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(2 * cores);

    for (final RepositoryConfig repositoryConfig : config.getRepositories()) {
      Worker worker = new Worker(config, repositoryConfig, args.isDoInit(), args.isDoUpdate());
      worker.registerObserver(new LoggingWorkerObserver(args, output, repositoryPadding));
      worker.registerObserver(new CliWorkerObserver());
      executor.submit(worker);
    }
    executor.shutdown();

    while (!executor.isTerminated()) {
      synchronized (output) {
        output.rotateSpinner();
      }

      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        break;
      }
    }
  }

  private class CliWorkerObserver extends AbstractWorkerObserver {
    @Override
    public void end(Worker worker) {
      try {
        List<String> localBranchNames = worker.getLocalBranchNames();
        Map<String, List<String>> remoteBranchNames = worker.getRemoteBranchNames();

        if (localBranchNames.isEmpty() && remoteBranchNames.isEmpty()) {
          return;
        }

        Map<String, Update> branchUpdateStatus = worker.getBranchUpdateStatus();
        Map<String, FromToIsh> branchUpdateIsh = worker.getBranchUpdateIsh();
        Map<String, Worker.Stats> branchStats = worker.getBranchStats();

        int padding = localBranchNames.stream().mapToInt(String::length).max().getAsInt();
        String pattern = "%" + padding + "s";

        synchronized (output) {
          output.deleteSpinner();

          output
              .print(" * ")
              .print(Color.YELLOW, worker.getRepositoryConfig().getName())
              .println();

          for (String branchName : localBranchNames) {
            Update update = branchUpdateStatus.get(branchName);
            Worker.Stats stats = branchStats.get(branchName);

            output
                .print("   ")
                .print(pattern, branchName);

            if (null != update) {
              Color color = Color.YELLOW;
              switch (update) {
                case SKIP_NO_UPSTREAM:
                  color = Color.LIGHT_GRAY;
                  break;

                case SKIP_UPSTREAM_DELETED:
                case SKIP_CONFLICTING:
                  color = Color.LIGHT_RED;
                  break;

                case UP_TO_DATE:
                case MERGED_FAST_FORWARD:
                case REBASED:
                  color = Color.GREEN;
                  break;
              }

              output
                  .print(" ")
                  .print(color, update.toString().toLowerCase().replace('_', ' '));

              switch (update) {
                case MERGED_FAST_FORWARD:
                case REBASED:
                  FromToIsh fromToIsh = branchUpdateIsh.get(branchName);
                  output
                      .print(" ")
                      .print(fromToIsh.getFrom().substring(0, 8))
                      .print("..")
                      .print(fromToIsh.getTo().substring(0, 8));
              }
            }

            if (0 == stats.getCommitsBehind() && 0 == stats.getCommitsAhead() && stats.isClean()) {
              output.print(Color.GREEN, "  ✔");
            }

            if (stats.getCommitsBehind() > 0) {
              output.print(Color.CYAN, "  ↓");
              output.print(Color.CYAN, stats.getCommitsBehind());
            }

            if (stats.getCommitsAhead() > 0) {
              output.print(Color.CYAN, "  ↑");
              output.print(Color.CYAN, stats.getCommitsAhead());
            }

            if (stats.getAdded() > 0) {
              output.print(Color.RED, "  +");
              output.print(Color.RED, stats.getAdded());
            }

            if (stats.getModified() > 0) {
              output.print(Color.YELLOW, "  ★");
              output.print(Color.YELLOW, stats.getModified());
            }

            if (stats.getRenamed() > 0) {
              output.print(Color.MAGENTA, "  ⇄");
              output.print(Color.MAGENTA, stats.getRenamed());
            }

            if (stats.getCopied() > 0) {
              output.print(Color.MAGENTA, "  ↷");
              output.print(Color.MAGENTA, stats.getCopied());
            }

            if (stats.getDeleted() > 0) {
              output.print(Color.MAGENTA, "  -");
              output.print(Color.MAGENTA, stats.getDeleted());
            }

            if (stats.getUnmerged() > 0) {
              output.print(Color.RED, "  ☠");
              output.print(Color.RED, stats.getUnmerged());
            }

            output.println();
          }

          output.println();
        }
      } catch (Exception exception) {
        logger.error(exception.getMessage(), exception);
      }
    }
  }
}
