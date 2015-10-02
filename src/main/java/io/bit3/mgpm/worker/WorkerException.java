package io.bit3.mgpm.worker;

public class WorkerException extends Exception {
  public WorkerException() {
  }

  public WorkerException(String message) {
    super(message);
  }

  public WorkerException(String message, Throwable cause) {
    super(message, cause);
  }

  public WorkerException(Throwable cause) {
    super(cause);
  }

  public WorkerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
