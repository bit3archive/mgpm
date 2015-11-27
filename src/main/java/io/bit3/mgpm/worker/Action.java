package io.bit3.mgpm.worker;

import io.bit3.mgpm.cmd.LogLevel;

public enum Action {
  ABORT(LogLevel.WARN),
  CLONE_REPOSITORY(LogLevel.WARN),
  STASH(LogLevel.DEBUG),
  UPDATE_REMOTE_URL(LogLevel.DEBUG),
  UNSTASH(LogLevel.DEBUG),
  PARSE_LOCAL_BRANCHES(LogLevel.DEBUG),
  PARSE_REMOTE_BRANCHES(LogLevel.DEBUG),
  DETERMINE_UPSTREAMS(LogLevel.DEBUG),
  FETCH_REMOTES(LogLevel.DEBUG),
  CHECKOUT(LogLevel.DEBUG),
  REBASE(LogLevel.DEBUG),
  REBASE_ABORT(LogLevel.WARN),
  MERGE(LogLevel.DEBUG),
  MERGE_ABORT(LogLevel.WARN),
  RESTORE_HEAD(LogLevel.DEBUG),
  UPDATE_SUBMODULES(LogLevel.DEBUG),
  EXCEPTION_OCCURRED(LogLevel.ERROR);

  public final LogLevel logLevel;

  Action(LogLevel logLevel) {
    this.logLevel = logLevel;
  }
}
