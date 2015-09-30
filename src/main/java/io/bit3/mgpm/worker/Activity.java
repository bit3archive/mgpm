package io.bit3.mgpm.worker;

public class Activity {
  private final Action action;
  private final String message;

  public Activity(Action action, String message) {
    this.action = action;
    this.message = message;
  }

  public Action getAction() {
    return action;
  }

  public String getMessage() {
    return message;
  }
}
