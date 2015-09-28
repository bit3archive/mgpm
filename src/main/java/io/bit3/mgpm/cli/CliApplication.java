package io.bit3.mgpm.cli;

import io.bit3.mgpm.cmd.Args;
import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.RepositoryConfig;
import io.bit3.mgpm.config.Strategy;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CliApplication {
  private static final Pattern STATUS_PATTERN = Pattern.compile("^([ ACDMRU\\?!])([ ADMU\\?!])");
  private final Logger logger;
  private final Args args;
  private final Config config;
  private AnsiOutput output;

  public CliApplication(Args args, Config config) {
    logger = LoggerFactory.getLogger(CliApplication.class);
    this.args = args;
    this.config = config;
  }

  public void run() {
    int cores = Runtime.getRuntime().availableProcessors();
    ExecutorService executor = Executors.newFixedThreadPool(2 * cores);
    Map<RepositoryConfig, Future<RepositoryStatus>> repositoryStatuses = new HashMap<>();

    for (final RepositoryConfig repositoryConfig : config.getRepositories()) {
      Future<RepositoryStatus> future = executor.submit(() -> getRepositoryStatus(repositoryConfig));
      repositoryStatuses.put(repositoryConfig, future);
    }

    int pad = 0;
    for (RepositoryConfig repository : config.getRepositories()) {
      pad = Math.max(pad, repository.getName().length());
    }
    pad++;

    output = new AnsiOutput(pad);

    List<File> repositoryDirectories = new LinkedList<>();

    for (RepositoryConfig repositoryConfig : config.getRepositories()) {
      output.setRepositoryConfig(repositoryConfig);

      if (args.isDoList() && !doList(repositoryConfig)) {
        continue;
      }

      RepositoryStatus repositoryStatus;

      try {
        repositoryStatus = repositoryStatuses.get(repositoryConfig).get();
      } catch (Exception e) {
        output.println(AnsiOutput.Color.RED, e.getMessage());
        continue;
      }

      if (null != repositoryStatus.error) {
        output.println(AnsiOutput.Color.RED, repositoryStatus.error);
        continue;
      }

      File directory = repositoryConfig.getDirectory();

      if (!directory.isDirectory()) {
        output.println(AnsiOutput.Color.YELLOW, "  missing");
        continue;
      }

      if (!new File(directory, ".git").isDirectory()) {
        output.println(AnsiOutput.Color.LIGHT_RED, "  not a git repository");
        continue;
      }

      repositoryDirectories.add(directory);

      String head;
      try {
        head = git(directory, "symbolic-ref", "HEAD", "--short");
      } catch (RuntimeException e) {
        head = git(directory, "rev-parse", "HEAD");
      }

      boolean stashRequired = !repositoryStatus.status.isClean();

      if (stashRequired) {
        output.println(AnsiOutput.Color.YELLOW, "Stashing changes");
        git(directory, "stash", "save", "-u");
      }

      for (String branchName : repositoryStatus.branchNames) {
        Upstream upstream = repositoryStatus.branchUpstreamMap.get(branchName);

        boolean hasUpstream = null != upstream && null != upstream.remoteBranch;

        if (head.equals(branchName)) {
          output.print(AnsiOutput.Color.YELLOW, "* ");
        } else {
          output.print("  ");
        }

        output.print(AnsiOutput.Color.CYAN, branchName);

        if ((hasUpstream || head.equals(branchName)) && (args.isDoUpdate() || args.isDoStat())) {
          doStat(repositoryConfig, branchName, head.equals(branchName), upstream.remoteName, upstream.remoteBranch);
        }

        if (args.isDoUpdate() && checkIfUpdatePossible(repositoryConfig)) {
          if (hasUpstream) {
            doUpdate(repositoryConfig, branchName, head);
          } else {
            output.print(AnsiOutput.Color.DARK_GRAY, " no upstream");
          }
        }

        output.println();
      }

      if (repositoryStatus.branchNames.contains(head)) {
        git(directory, "checkout", head);
      }

      if (stashRequired) {
        output.println(AnsiOutput.Color.YELLOW, "Unstashing changes");
        git(directory, "stash", "pop");
      }
    }

    File currentWorkingDirectory = Paths.get(".").toAbsolutePath().normalize().toFile();

    for (File child : currentWorkingDirectory.listFiles(File::isDirectory)) {
      if (!repositoryDirectories.contains(child)) {
        output.setRepositoryConfig(new RepositoryConfig(child.getName(), null, Strategy.HEAD));
        output.println(AnsiOutput.Color.YELLOW, "  superfluous");
      }
    }
  }

  private RepositoryStatus getRepositoryStatus(RepositoryConfig repositoryConfig) {
    RepositoryStatus repositoryStatus = new RepositoryStatus();

    if (args.isDoInit()) {
      try {
        repositoryStatus.initialized = doInit(repositoryConfig);
      } catch (InitialisationException exception) {
        repositoryStatus.error = exception.getMessage();
        return repositoryStatus;
      }
    }

    File directory = repositoryConfig.getDirectory();
    repositoryStatus.branchNames = parseBranches(git(directory, "branch"));
    Set<String> remoteNames = new HashSet<>();

    for (String branchName : repositoryStatus.branchNames) {
      try {
        String remoteName = getBranchRemote(directory, branchName);
        String remoteRef = git(directory, "config", "--local", "--get", String.format("branch.%s.merge", branchName));

        if (StringUtils.isEmpty(remoteName) || StringUtils.isEmpty(remoteRef)) {
          continue;
        }

        remoteNames.add(remoteName);
        repositoryStatus.branchUpstreamMap.put(branchName, new Upstream(remoteName, remoteRef));
      } catch (Exception e) {
        // exception means, there is no remote configured
      }
    }

    List<String> command = Arrays.asList("fetch", "--multiple");
    command.addAll(remoteNames);
    git(directory, command);
    repositoryStatus.fetchedRemotes.addAll(remoteNames);

    repositoryStatus.status = getStatus(directory);

    return repositoryStatus;
  }

  private Status getStatus(File directory) {
    return parseStatus(git(directory, "status", "--porcelain"));
  }

  private String getBranchRemote(File directory, String branchName) {
    return git(directory, "config", "--local", "--get", String.format("branch.%s.remote", branchName));
  }

  private boolean doList(RepositoryConfig repository) {
    output.println(repository.getUrl());
    output.println(repository.getDirectory().toString());
    return true;
  }

  private boolean doInit(RepositoryConfig repository) throws InitialisationException {
    File directory = repository.getDirectory();

    if (directory.exists()) {
      if (!directory.isDirectory()) {
        logger.warn("[{}] ignoring, is not a directory!", directory);
        throw new InitialisationException(String.format("ignoring, \"%s\" is not a directory!", directory));
      }

      if (new File(directory, ".git").isDirectory()) {
        String actualUrl = git(directory, "config", "--local", "--get", "remote.origin.url");
        String expectedUrl = repository.getUrl();

        if (!expectedUrl.equals(actualUrl)) {
          logger.info("[{}] update remote url", directory);
          git(directory, "remote", "set-url", "origin", expectedUrl);
        }

        return false;
      }

      File[] children = directory.listFiles();
      if (null == children) {
        logger.warn("[{}] ignoring, the directory could not be listed", directory);
        throw new InitialisationException(String.format("ignoring, the directory \"%s\" could not be listed", directory));
      }
      if (0 < children.length) {
        logger.warn("[{}] ignoring, the directory is not empty", directory);
        throw new InitialisationException(String.format("ignoring, the directory \"%s\" is not empty", directory));
      }
    }

    git(directory.getParentFile(), "clone", repository.getUrl(), directory.toString());
    git(directory, "submodule", "init");
    git(directory, "submodule", "update");

    return true;
  }

  private boolean checkIfUpdatePossible(RepositoryConfig repositoryConfig) {
    File directory = repositoryConfig.getDirectory();

    Status status = getStatus(directory);

    int conflicts = status.index.unmerged + status.workingTree.unmerged;
    int index = status.index.total();
    int workingTree = status.workingTree.total();

    if (conflicts > 0) {
      output.print(AnsiOutput.Color.LIGHT_RED, "  cannot update, has conflicts");
      return false;
    }

    if (index > 0 || workingTree > 0) {
      output.print(AnsiOutput.Color.LIGHT_RED, "  cannot update, has changes");
      return false;
    }

    return true;
  }

  private boolean doUpdate(RepositoryConfig repositoryConfig, String branchName, String head) {
    File directory = repositoryConfig.getDirectory();

    try {
      git(directory, "checkout", branchName);
      git(directory, "submodule", "sync");
      git(directory, "submodule", "update");
      git(directory, "pull");
      git(directory, "checkout", head);
      output.print(AnsiOutput.Color.GREEN, " updated");
    } catch (RuntimeException exception) {
      output.print(AnsiOutput.Color.LIGHT_RED, " " + exception.getMessage());
      return false;
    }

    return true;
  }

  private boolean doStat(RepositoryConfig repository, String branchName, boolean isHead, String remoteName, String remoteBranch) {
    File directory = repository.getDirectory();

    int commitsBehind = 0;
    int commitsAhead = 0;
    int conflicts = 0;
    int index = 0;
    int workingTree = 0;

    if (null != remoteName && null != remoteBranch) {
      // remove "refs/heads/"
      remoteBranch = remoteBranch.substring(11);
      // prepend remote name
      remoteBranch = remoteName + "/" + remoteBranch;

      String localRef = git(directory, "rev-parse", branchName);
      String remoteRef = git(directory, "rev-parse", remoteBranch);

      commitsBehind = Integer.parseInt(
          git(directory, "rev-list", "--count", String.format("%s..%s", localRef, remoteRef))
      );

      commitsAhead = Integer.parseInt(
          git(directory, "rev-list", "--count", String.format("%s..%s", remoteRef, localRef))
      );
    }

    if (isHead) {
      Status status = getStatus(directory);

      conflicts = status.index.unmerged + status.workingTree.unmerged;
      index = status.index.total();
      workingTree = status.workingTree.total();
    }

    formatStats(commitsBehind, commitsAhead, conflicts, index, workingTree);

    return true;
  }

  private void outputStats(File directory, String branchName, String remoteName, String remoteBranch) {

  }

  private void formatStats(int commitsBehind, int commitsAhead, int conflicts, int index, int workingTree) {
    if (0 == commitsBehind && 0 == commitsAhead && 0 == conflicts && 0 == index && 0 == workingTree) {
      output.print(AnsiOutput.Color.GREEN, "  ✔");
      return;
    }

    if (commitsBehind > 0) {
      output.print(AnsiOutput.Color.CYAN, "  ↓");
      output.print(AnsiOutput.Color.CYAN, commitsBehind);
    }

    if (commitsAhead > 0) {
      output.print(AnsiOutput.Color.CYAN, "  ↑");
      output.print(AnsiOutput.Color.CYAN, commitsAhead);
    }

    if (conflicts > 0) {
      output.print(AnsiOutput.Color.RED, "  ☠");
      output.print(AnsiOutput.Color.RED, conflicts);
    }

    if (index > 0) {
      output.print(AnsiOutput.Color.YELLOW, "  ★");
      output.print(AnsiOutput.Color.YELLOW, index);
    }

    if (workingTree > 0) {
      output.print(AnsiOutput.Color.MAGENTA, "  +");
      output.print(AnsiOutput.Color.MAGENTA, workingTree);
    }
  }

  private String git(File directory, List<String> arguments) {
    return git(directory, arguments.toArray(new String[arguments.size()]));
  }

  private String git(File directory, String... arguments) {
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

  private List<String> parseBranches(String gitOutput) {
    String[] lines = gitOutput.split("\n");
    return Arrays.asList(lines)
        .stream()
        .map((String branch) -> branch.replaceFirst("^\\*", "").trim())
        .filter(branch -> !branch.isEmpty())
        .sorted()
        .collect(Collectors.toList());
  }

  private Status parseStatus(String gitOutput) {
    Status status = new Status();

    String[] lines = gitOutput.split("\n");
    for (String line : lines) {
      Matcher matcher = STATUS_PATTERN.matcher(line);

      if (matcher.find()) {
        char index = matcher.group(1).charAt(0);
        char workingTree = matcher.group(2).charAt(0);

        switch (index) {
          case 'A':
          case 'C':
            status.index.added++;
            break;
          case 'D':
            status.index.deleted++;
            break;
          case 'M':
            status.index.modified++;
            break;
          case 'R':
            status.index.renamed++;
            break;
          case 'U':
            status.index.unmerged++;
            break;
        }

        switch (workingTree) {
          case 'A':
          case '?':
            status.workingTree.added++;
            break;
          case 'D':
            status.workingTree.deleted++;
            break;
          case 'M':
            status.workingTree.modified++;
            break;
          case 'U':
            status.workingTree.unmerged++;
            break;
        }
      }
    }

    return status;
  }

  private static class Upstream {
    private final String remoteName;
    private final String remoteBranch;
    private final String remoteRef;

    public Upstream(String remoteName, String remoteRef) {
      this.remoteName = remoteName;
      this.remoteBranch = remoteRef.startsWith("refs/heads/") ? remoteRef.substring(11) : null;
      this.remoteRef = remoteRef;
    }
  }

  private static class RepositoryStatus {
    private String error = null;
    private boolean initialized = false;
    private Status status = null;
    private Set<String> fetchedRemotes = new HashSet<>();
    private List<String> branchNames = new LinkedList<>();
    private Map<String, Upstream> branchUpstreamMap = new HashMap<>();
  }

  private static class Status {
    private final Stat index = new Stat();
    private final Stat workingTree = new Stat();

    public int total() {
      return index.total() + workingTree.total();
    }

    public boolean isClean() {
      return 0 == total();
    }
  }

  private static class Stat {
    private int added = 0;
    private int modified = 0;
    private int renamed = 0;
    private int deleted = 0;
    private int unmerged = 0;

    public int total() {
      return added + modified + renamed + deleted + unmerged;
    }
  }

  private static class InitialisationException extends Exception {
    public InitialisationException() {
    }

    public InitialisationException(String message) {
      super(message);
    }

    public InitialisationException(String message, Throwable cause) {
      super(message, cause);
    }

    public InitialisationException(Throwable cause) {
      super(cause);
    }

    public InitialisationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }
  }
}
