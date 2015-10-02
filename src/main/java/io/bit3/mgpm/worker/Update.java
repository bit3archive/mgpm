package io.bit3.mgpm.worker;

public enum Update {
  SKIP_NO_UPSTREAM,
  SKIP_UPSTREAM_DELETED,
  UP_TO_DATE,
  MERGED_FAST_FORWARD,
  REBASED,
  SKIP_CONFLICTING
}
