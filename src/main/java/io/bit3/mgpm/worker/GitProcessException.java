package io.bit3.mgpm.worker;

public class GitProcessException extends Exception {
  public GitProcessException() {
  }

  public GitProcessException(String message) {
    super(message);
  }

  public GitProcessException(String message, Throwable cause) {
    super(message, cause);
  }

  public GitProcessException(Throwable cause) {
    super(cause);
  }

  public GitProcessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
