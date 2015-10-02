package io.bit3.mgpm.worker;

public abstract class AbstractWorkerObserver implements WorkerObserver {
  @Override
  public void start(Worker worker) {
    // no op
  }

  @Override
  public void activity(Activity activity, Worker worker) {
    // no op
  }

  @Override
  public void end(Worker worker) {
    // no op
  }
}
