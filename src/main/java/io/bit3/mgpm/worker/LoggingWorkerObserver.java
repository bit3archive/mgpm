package io.bit3.mgpm.worker;

import io.bit3.mgpm.cli.AnsiOutput;
import io.bit3.mgpm.cli.Color;
import io.bit3.mgpm.cmd.Args;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingWorkerObserver extends AbstractWorkerObserver {
  private final Logger logger = LoggerFactory.getLogger(LoggingWorkerObserver.class);
  private final Args args;
  private final AnsiOutput output;
  private final int repositoryPadding;
  private final String printPattern;

  public LoggingWorkerObserver(Args args, AnsiOutput output, int repositoryPadding) {
    this.args = args;
    this.output = output;
    this.repositoryPadding = repositoryPadding;
    printPattern = "%" + repositoryPadding + "s";
  }

  @Override
  public void activity(Activity activity, Worker worker) {
    switch (activity.getAction().logLevel) {
      case TRACE:
        if (logger.isTraceEnabled()) {
          break;
        }
        return;

      case DEBUG:
        if (logger.isDebugEnabled()) {
          break;
        }
        return;

      case INFO:
        if (logger.isInfoEnabled()) {
          break;
        }
        return;

      case WARN:
        if (logger.isWarnEnabled()) {
          break;
        }
        return;

      case ERROR:
        if (logger.isErrorEnabled()) {
          break;
        }
        return;
    }

    synchronized (output) {
      output
          .print("[LOG] ")
          .print(printPattern, worker.getRepositoryConfig().getName())
          .print(Color.YELLOW, " [%s] ", activity.getAction().toString())
          .print(activity.getMessage())
          .println();
    }
  }
}
