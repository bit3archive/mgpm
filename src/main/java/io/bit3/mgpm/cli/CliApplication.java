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
import java.util.*;
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
      if (args.isDoInit() && !doInit(repositoryConfig)) {
        continue;
      }

      File directory = repositoryConfig.getDirectory();

      if (!directory.isDirectory() || !new File(directory, ".git").isDirectory()) {
        continue;
      }

      if (!args.isDoStat() && (!args.isDoUpdate() || !checkIfUpdatePossible(repositoryConfig))) {
        continue;
      }

      repositoryDirectories.add(directory);

      Set<String> fetchedRemotes = new HashSet<>();
      List<String> branchNames = parseBranches(git(directory, "branch"));

      String head;
      try {
        head = git(directory, "symbolic-ref", "HEAD", "--short");
      } catch (RuntimeException e) {
        head = git(directory, "rev-parse", "HEAD");
      }

      for (String branchName : branchNames) {
        String remoteName = null;
        String remoteBranch = null;

        try {
          remoteName = git(directory, "config", "--local", "--get", String.format("branch.%s.remote", branchName));
        } catch (Exception e) {
          // exception means, there is no remote configured
        }

        try {
          remoteBranch = git(directory, "config", "--local", "--get", String.format("branch.%s.merge", branchName));
        } catch (Exception e) {
          // exception means, there is no remote configured
        }

        boolean hasUpstream = !(
                null == remoteName
            || "".equals(remoteName)
            || null == remoteBranch
            || "".equals(remoteBranch)
            || !remoteBranch.startsWith("refs/heads/"));

        if (hasUpstream && !fetchedRemotes.contains(remoteName)) {
          git(directory, "fetch", remoteName);
          fetchedRemotes.add(remoteName);
        }

        output.print(AnsiOutput.Color.CYAN, branchName);

        if (head.equals(branchName)) {
          output.print(AnsiOutput.Color.YELLOW, " *");
        }

        if (args.isDoUpdate()) {
          if (hasUpstream) {
            doUpdate(repositoryConfig, branchName, head);
          } else {
            output.print(AnsiOutput.Color.DARK_GRAY, " no upstream");
          }
        }

        if ((hasUpstream || head.equals(branchName)) && (args.isDoUpdate() || args.isDoStat())) {
          doStat(repositoryConfig, branchName, head.equals(branchName), remoteName, remoteBranch);
        } else if (args.isDoUpdate()) {
          output.println();
        }
      }

      if (branchNames.contains(head)) {
        git(directory, "checkout", head);
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

  private boolean doList(RepositoryConfig repository) {
    output.println(repository.getUrl());
    output.println(repository.getDirectory().toString());
    return true;
  }

  private boolean doInit(RepositoryConfig repository) {
    File directory = repository.getDirectory();

    if (directory.exists()) {
      if (!directory.isDirectory()) {
        logger.warn("[{}] ignoring, is not a directory!", directory);
        output.println(AnsiOutput.Color.RED, "ignoring, \"%s\" is not a directory!", directory);
        return false;
      }

      if (new File(directory, ".git").isDirectory()) {
        String actualUrl = git(directory, "config", "--local", "--get", "remote.origin.url");
        String expectedUrl = repository.getUrl();

        if (!expectedUrl.equals(actualUrl)) {
          output.println("update remote url");
          git(directory, "remote", "set-url", "origin", expectedUrl);
        }

        return true;
      }

      File[] children = directory.listFiles();
      if (null == children) {
        logger.warn("[{}] ignoring, the directory could not be listed", directory);
        return false;
      }
      if (0 < children.length) {
        logger.warn("[{}] ignoring, the directory is not empty", directory);
        return false;
      }
    }

    output.print("cloning.");
    git(directory.getParentFile(), "clone", repository.getUrl(), directory.toString());
    git(directory, "submodule", "init");
    output.print(".");
    git(directory, "submodule", "update");
    output.println(".done");

    return true;
  }

  private boolean checkIfUpdatePossible(RepositoryConfig repositoryConfig) {
    File directory = repositoryConfig.getDirectory();

    Status status = parseStatus(git(directory, "status", "--porcelain"));

    int conflicts = status.index.unmerged + status.workingTree.unmerged;
    int index = status.index.total();
    int workingTree = status.workingTree.total();

    if (conflicts > 0) {
      output.println(AnsiOutput.Color.RED, " cannot update, has conflicts");
      return false;
    }

    if (index > 0 || workingTree > 0) {
      output.println(AnsiOutput.Color.RED, " cannot update, has changes");
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
    } finally {
      git(directory, "checkout", head);
    }

    output.print(AnsiOutput.Color.GREEN, " updated");

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
      Status status = parseStatus(git(directory, "status", "--porcelain"));

      conflicts = status.index.unmerged + status.workingTree.unmerged;
      index = status.index.total();
      workingTree = status.workingTree.total();
    }

    formatStats(commitsBehind, commitsAhead, conflicts, index, workingTree);

    output.println();

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

  private static class Status {
    private final Stat index = new Stat();
    private final Stat workingTree = new Stat();
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
}
