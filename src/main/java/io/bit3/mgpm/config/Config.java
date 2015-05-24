package io.bit3.mgpm.config;

import java.util.LinkedList;
import java.util.List;

public class Config {
  private final GitConfig gitConfig;
  private final GithubConfig githubConfig;
  private final List<RepositoryConfig> repositories;

  public Config() {
    gitConfig = new GitConfig();
    githubConfig = new GithubConfig();
    repositories = new LinkedList<>();
  }

  public GitConfig getGitConfig() {
    return gitConfig;
  }

  public GithubConfig getGithubConfig() {
    return githubConfig;
  }

  public List<RepositoryConfig> getRepositories() {
    return repositories;
  }
}
