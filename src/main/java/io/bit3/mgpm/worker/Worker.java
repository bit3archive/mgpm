package io.bit3.mgpm.worker;

import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.RepositoryConfig;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Worker implements Runnable {
  private final Logger logger = LoggerFactory.getLogger(Worker.class);
  private final List<WorkerObserver> observers = new LinkedList<>();
  private final List<Activity> journal = new LinkedList<>();
  private final Config config;
  private final RepositoryConfig repositoryConfig;
  private final boolean cloneIfNotExists;
  private final Set<String> remoteNames = new HashSet<>();
  private final List<String> localBranchNames = new LinkedList<>();
  private final Map<String, List<String>> remoteBranchNames = new HashMap<>();
  private final Map<String, Upstream> branchUpstreamMap = new HashMap<>();
  private final Map<String, Update> branchUpdateStatus = new HashMap<>();
  private final Map<String, Stats> branchStats = new HashMap<>();
  private String headSymbolicRef;
  private String headCommitRef;
  private boolean updateExisting;
  private boolean hasStashed = false;
  private boolean succeed = false;

  public Worker(Config config, RepositoryConfig repositoryConfig, boolean cloneIfNotExists, boolean updateExisting) {
    this.config = config;
    this.repositoryConfig = repositoryConfig;
    this.cloneIfNotExists = cloneIfNotExists;
    this.updateExisting = updateExisting;
  }

  public void registerObserver(WorkerObserver observer) {
    observers.add(observer);
  }

  public void unregisterObserver(WorkerObserver observer) {
    observers.remove(observer);
  }

  public List<Activity> getJournal() {
    return journal;
  }

  public Config getConfig() {
    return config;
  }

  public RepositoryConfig getRepositoryConfig() {
    return repositoryConfig;
  }

  public List<String> getLocalBranchNames() {
    return localBranchNames;
  }

  public Map<String, List<String>> getRemoteBranchNames() {
    return remoteBranchNames;
  }

  public Map<String, Upstream> getBranchUpstreamMap() {
    return branchUpstreamMap;
  }

  public Map<String, Update> getBranchUpdateStatus() {
    return branchUpdateStatus;
  }

  public Map<String, Stats> getBranchStats() {
    return branchStats;
  }

  public String getHeadSymbolicRef() {
    return headSymbolicRef;
  }

  public String getHeadCommitRef() {
    return headCommitRef;
  }

  public boolean isSucceed() {
    return succeed;
  }

  @Override
  public void run() {
    for (WorkerObserver observer : observers) {
      observer.start(this);
    }

    try {
      if (cloneOrReconfigureRepository()) {
        determineHead();
        stashChanges();
        determineBranchesAndUpstreams();
        fetchRemotes();
        updateBranches();
        restoreHead();
        unstashChanges();
        succeed = true;
      }
    } catch (Exception exception) {
      logger.error(exception.getMessage(), exception);
      journal.add(new Activity(Action.EXCEPTION_OCCURRED, exception.getMessage()));
    }

    for (WorkerObserver observer : observers) {
      observer.end(this);
    }
  }

  /**
   * Clone or reconfigure the repository if necessary.
   */
  private boolean cloneOrReconfigureRepository() throws WorkerException, GitProcessException {
    File directory = repositoryConfig.getDirectory();

    if (directory.exists()) {
      if (!directory.isDirectory()) {
        throw new WorkerException(String.format("ignoring, \"%s\" is not a directory!", directory));
      }

      if (new File(directory, ".git").isDirectory()) {
        String actualUrl = git("config", "--local", "--get", "remote.origin.url");
        String expectedUrl = repositoryConfig.getUrl();

        if (!expectedUrl.equals(actualUrl)) {
          activity(Action.UPDATE_REMOTE_URL, "[{}] update remote url", directory);
          git("remote", "set-url", "origin", expectedUrl);
        }

        return true;
      }

      File[] children = directory.listFiles();
      if (null == children) {
        logger.warn("[{}] ignoring, the directory could not be listed", directory);
        throw new WorkerException(String.format("ignoring, the directory \"%s\" could not be listed", directory));
      }

      if (0 < children.length) {
        logger.warn("[{}] ignoring, the directory is not empty", directory);
        throw new WorkerException(String.format("ignoring, the directory \"%s\" is not empty", directory));
      }
    }

    if (!cloneIfNotExists) {
      activity(Action.ABORT, "not cloned yet");
      return false;
    }

    git(directory.getParentFile(), "clone", repositoryConfig.getUrl(), directory.toString());
    git("submodule", "init");
    git("submodule", "update");

    updateExisting = false;
    return true;
  }

  /**
   * Determine the current HEAD state.
   */
  private void determineHead() throws GitProcessException {
    try {
      headSymbolicRef = git("symbolic-ref", "HEAD", "--short");
      headCommitRef = git("rev-parse", headSymbolicRef);
    } catch (GitProcessException e) {
      headSymbolicRef = headCommitRef = git("rev-parse", "HEAD");
    }
  }

  /**
   * Stash changes, if necessary.
   */
  private void stashChanges() throws GitProcessException {
    if (StringUtils.isEmpty(git("status", "--porcelain"))) {
      return;
    }

    activity(Action.STASH, "stash changes");
    git("stash", "save", "--include-untracked", "--all", "Stash before mgpm update");
    hasStashed = true;
  }

  /**
   * Unstash changes, if necessary.
   */
  private void unstashChanges() throws GitProcessException {
    if (!hasStashed) {
      return;
    }

    activity(Action.UNSTASH, "apply stash");
    git("stash", "pop");
  }

  /**
   * Determine local and remote branches and upstreams.
   */
  private void determineBranchesAndUpstreams() throws GitProcessException {
    activity(Action.PARSE_LOCAL_BRANCHES, "parse local branches");
    localBranchNames.addAll(parseLocalBranches(git("branch")));

    activity(Action.PARSE_REMOTE_BRANCHES, "parse remote branches");
    remoteBranchNames.putAll(parseRemoteBranches(git("branch", "-r")));

    activity(Action.DETERMINE_UPSTREAMS, "determine branch upstreams");
    for (String branchName : localBranchNames) {
      String remoteName = null;
      String remoteRef = null;
      String rebase = null;

      try {
        remoteName = git("config", "--local", "--get", String.format("branch.%s.remote", branchName));
      } catch (GitProcessException e) {
        // exception means, there is no remote configured
      }

      try {
        remoteRef = git("config", "--local", "--get", String.format("branch.%s.merge", branchName));
      } catch (GitProcessException e) {
        // exception means, there is no remote configured
      }

      try {
        rebase = git("config", "--local", "--get", String.format("branch.%s.rebase", branchName)).toLowerCase();
      } catch (GitProcessException e) {
        // exception means, there is no remote configured
      }

      if (StringUtils.isEmpty(remoteName) || StringUtils.isEmpty(remoteRef)) {
        continue;
      }

      String remoteBranch = null;

      if (remoteRef.startsWith("refs/heads/")) {
        remoteBranch = remoteRef.substring(11);
        remoteRef = remoteName + "/" + remoteBranch;
      } else {
        remoteRef = remoteName + "/" + remoteRef;
      }

      remoteNames.add(remoteName);
      branchUpstreamMap.put(branchName, new Upstream(remoteName, remoteBranch, remoteRef, "true".equals(rebase)));
    }
  }

  /**
   * Fetch all tracked remotes.
   */
  private void fetchRemotes() throws GitProcessException {
    activity(Action.FETCH_REMOTES, "fetch remotes {}", String.join(", ", remoteNames));

    List<String> command = Arrays.asList("fetch", "--prune", "--multiple");
    command.addAll(remoteNames);
    git(command);
  }

  private void determineStats() throws GitProcessException {
    for (String branchName : localBranchNames) {
      determineStats(branchName);
    }
  }

  private void determineStats(String branchName) throws GitProcessException {
    Upstream upstream = branchUpstreamMap.get(branchName);

    if (!determineUpstreamIsAvailable(upstream)) {
      return;
    }

    Stats stats = new Stats();

    String localRef = git("rev-parse", branchName);
    String remoteRef = git("rev-parse", upstream.remoteRef);

    stats.commitsBehind = Integer.parseInt(
        git("rev-list", "--count", String.format("%s..%s", localRef, remoteRef))
    );

    stats.commitsAhead = Integer.parseInt(
        git("rev-list", "--count", String.format("%s..%s", remoteRef, localRef))
    );

    if (headCommitRef.equals(localRef)) {
      String status = git("status", "--porcelain");

      for (String line : status.split("\n")) {
        char index = line.charAt(0);
        char workTree = line.charAt(1);

        updateStats(stats, index);
        updateStats(stats, workTree);
      }
    }

    branchStats.put(branchName, stats);
  }

  private void updateStats(Stats stats, char status) {
    switch (status) {
      case 'M':
        stats.modified++;
        break;

      case 'A':
        stats.added++;
        break;

      case 'D':
        stats.deleted++;
        break;

      case 'R':
        stats.renamed++;
        break;

      case 'C':
        stats.copied++;
        break;

      case 'U':
        stats.unmerged++;
        break;
    }
  }

  /**
   * Update all branches.
   */
  private void updateBranches() throws GitProcessException {
    for (String branchName : localBranchNames) {
      updateBranch(branchName);
    }
  }

  private void updateBranch(String branchName) throws GitProcessException {
    Upstream upstream = branchUpstreamMap.get(branchName);

    if (!determineUpstreamIsAvailable(upstream)) {
      branchUpdateStatus.put(branchName, Update.SKIP_NO_UPSTREAM);
      return;
    }

    activity(Action.CHECKOUT, "checkout branch {}", branchName);
    git("checkout", branchName);

    if (upstream.rebase) {
      activity(Action.REBASE, "rebase onto {}", upstream.remoteRef);

      try {
        git("rebase", upstream.remoteRef);
        branchUpdateStatus.put(branchName, Update.REBASED);
      } catch (GitProcessException exception) {
        activity(Action.REBASE_ABORT, "rebase aborted");
        git("rebase", "--abort");
        branchUpdateStatus.put(branchName, Update.SKIP_CONFLICTING);
      }
    } else {
      activity(Action.MERGE, "merge branch {}", upstream.remoteRef);

      try {
        git("merge", "--ff-only", upstream.remoteRef);
        branchUpdateStatus.put(branchName, Update.MERGED);
      } catch (GitProcessException exception) {
        activity(Action.MERGE_ABORT, "merge aborted");
        git("merge", "--abort");
        branchUpdateStatus.put(branchName, Update.SKIP_CONFLICTING);
      }
    }

    if (new File(repositoryConfig.getDirectory(), ".gitmodules").isFile()) {
      activity(Action.UPDATE_SUBMODULES, "update submodules");
      git("submodule", "sync");
      git("submodule", "update");
    }
  }

  private boolean determineUpstreamIsAvailable(Upstream upstream) {
    if (null == upstream) {
      return false;
    }

    List<String> branchNames = remoteBranchNames.get(upstream.remoteName);

    return null != branchNames && branchNames.contains(upstream.remoteBranch);
  }

  /**
   * Restore original HEAD state.
   */
  private void restoreHead() throws GitProcessException {
    activity(Action.RESTORE_HEAD, "restore {}", headSymbolicRef);
    git("checkout", headSymbolicRef);
  }

  /**
   * Record an activity to the journal and into the log.
   *
   * @param action    The performed action.
   * @param message   The message.
   * @param arguments Multiple message arguments.
   */
  private void activity(Action action, String message, Object... arguments) {
    message = MessageFormatter.arrayFormat(message, arguments).getMessage();
    logger.info("[{}] {}: {}", repositoryConfig.getName(), action, message);
    Activity activity = new Activity(action, message);
    journal.add(activity);

    for (WorkerObserver observer : observers) {
      observer.activity(activity, this);
    }
  }

  private String git(List<String> arguments) throws GitProcessException {
    return git(repositoryConfig.getDirectory(), arguments);
  }

  private String git(String... arguments) throws GitProcessException {
    return git(repositoryConfig.getDirectory(), arguments);
  }

  private String git(File directory, List<String> arguments) throws GitProcessException {
    return git(arguments.toArray(new String[arguments.size()]));
  }

  private String git(File directory, String... arguments) throws GitProcessException {
    List<String> command = new LinkedList<>();
    command.add(config.getGitConfig().getBinary());
    command.addAll(Arrays.asList(arguments));

    logger.debug("[{}] > {}", directory, String.join(" ", command));

    try {
      Process process = new ProcessBuilder()
          .directory(directory)
          .command(command)
          .start();
      int exitCode = process.waitFor();

      if (0 != exitCode) {
        String error = IOUtils.toString(process.getErrorStream()).trim();

        if (StringUtils.isEmpty(error)) {
          error = IOUtils.toString(process.getInputStream()).trim();
        }

        String message = String.format(
            "execution of \"%s\" in \"%s\" failed with exit code %d: %s",
            String.join(" ", command),
            directory.getAbsolutePath(),
            exitCode,
            error
        );

        throw new RuntimeException(message);
      }

      return IOUtils.toString(process.getInputStream()).replaceAll("\\s+$", "");
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private List<String> parseLocalBranches(String gitOutput) {
    String[] lines = gitOutput.split("\n");
    return Arrays.asList(lines)
        .stream()
        .map(branch -> branch.replaceFirst("^\\*", "").trim())
        .filter(branch -> !branch.isEmpty())
        .sorted()
        .collect(Collectors.toList());
  }

  private Map<String, List<String>> parseRemoteBranches(String gitOutput) {
    String[] lines = gitOutput.split("\n");
    return Arrays.asList(lines)
        .stream()
        .map(String::trim)
        .filter(branch -> !(branch.isEmpty() || branch.contains(" -> ")))
        .sorted()
        .map(branch -> branch.split("/", 2))
        .collect(Collectors.toMap(
            chunks -> chunks[0],
            chunks -> Collections.singletonList(chunks[1]),
            (left, right) -> {
              left.addAll(right);
              return left;
            }
        ));
  }

  public enum Update {
    SKIP_NO_UPSTREAM,
    SKIP_UPSTREAM_DELETED,
    MERGED,
    REBASED,
    SKIP_CONFLICTING
  }

  private static class Upstream {
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
  }

  private static class Stats {
    private int commitsBehind = 0;
    private int commitsAhead = 0;
    private int added = 0;
    private int modified = 0;
    private int renamed = 0;
    private int copied = 0;
    private int deleted = 0;
    private int unmerged = 0;
  }

  private class GitProcessException extends Exception {
  }

}
