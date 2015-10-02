package io.bit3.mgpm.config;

import java.io.File;
import java.nio.file.Paths;

public class RepositoryConfig {
  private String name;
  private String url;
  private Strategy strategy;
  private File directory;

  public RepositoryConfig(String name, String url, Strategy strategy) {
    this.name = name;
    this.url = url;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public void setStrategy(Strategy strategy) {
    this.strategy = strategy;
  }

  public File getDirectory() {
    if (null == directory) {
      directory = new File(Paths.get(".").toAbsolutePath().normalize().toString(), name);
    }
    return directory;
  }

  public void setDirectory(File directory) {
    this.directory = directory;
  }

  @Override
  public int hashCode() {
    return getDirectory().hashCode();
  }

  @Override
  public String toString() {
    return getDirectory().toString();
  }
}
