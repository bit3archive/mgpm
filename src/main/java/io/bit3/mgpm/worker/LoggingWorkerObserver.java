package io.bit3.mgpm.worker;

import io.bit3.mgpm.cli.AnsiOutput;
import io.bit3.mgpm.cli.Color;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingWorkerObserver extends AbstractWorkerObserver {
  private final Logger logger = LoggerFactory.getLogger(LoggingWorkerObserver.class);
  private final AnsiOutput output;

  public LoggingWorkerObserver(AnsiOutput output) {
    this.output = output;
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
          .print(worker.getRepositoryConfig().getPathName())
          .print(Color.YELLOW, " [%s] ", activity.getAction().toString())
          .print(activity.getMessage())
          .println();
    }
  }
}
