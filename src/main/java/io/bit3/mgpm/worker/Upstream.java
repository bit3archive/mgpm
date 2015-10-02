package io.bit3.mgpm.worker;

public class Upstream {
  private final String remoteName;
  private final String remoteBranch;
  private final String remoteRef;
  private final boolean rebase;

  public Upstream(String remoteName, String remoteBranch, String remoteRef, boolean rebase) {
    this.remoteName = remoteName;
    this.rebase = rebase;
    this.remoteBranch = remoteBranch;
    this.remoteRef = remoteRef;
  }

  public String getRemoteName() {
    return remoteName;
  }

  public String getRemoteBranch() {
    return remoteBranch;
  }

  public String getRemoteRef() {
    return remoteRef;
  }

  public boolean isRebase() {
    return rebase;
  }
}
