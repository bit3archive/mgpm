package io.bit3.mgpm.worker;

public interface WorkerObserver {
  void start(Worker worker);

  void activity(Activity activity, Worker worker);

  void end(Worker worker);
}
