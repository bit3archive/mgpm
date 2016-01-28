package io.bit3.mgpm.cmd;

import java.io.File;

public class Args {
  private File config = null;
  private boolean doInit = false;
  private boolean doUpdate = false;
  private boolean showStatus = false;
  private boolean showGui = false;
  private boolean omitSuperfluousWarnings = false;
  private int threads = 2 * Runtime.getRuntime().availableProcessors();
  private LogLevel loggerLevel = LogLevel.TRACE;

  public boolean hasConfig() {
    return null != config;
  }

  public File getConfig() {
    return config;
  }

  public void setConfig(File config) {
    this.config = config;
  }

  public boolean isDoInit() {
    return doInit;
  }

  public void setDoInit(boolean doInit) {
    this.doInit = doInit;
  }

  public boolean isDoUpdate() {
    return doUpdate;
  }

  public void setDoUpdate(boolean doUpdate) {
    this.doUpdate = doUpdate;
  }

  public boolean isShowStatus() {
    return showStatus;
  }

  public void setShowStatus(boolean showStatus) {
    this.showStatus = showStatus;
  }

  public boolean isShowGui() {
    return showGui;
  }

  public void setShowGui(boolean showGui) {
    this.showGui = showGui;
  }

  public boolean isOmitSuperfluousWarnings() {
    return omitSuperfluousWarnings;
  }

  public void setOmitSuperfluousWarnings(boolean omitSuperfluousWarnings) {
    this.omitSuperfluousWarnings = omitSuperfluousWarnings;
  }

  public int getThreads() {
    return threads;
  }

  public void setThreads(int threads) {
    this.threads = threads;
  }

  public LogLevel getLoggerLevel() {
    return loggerLevel;
  }

  public void setLoggerLevel(LogLevel loggerLevel) {
    this.loggerLevel = loggerLevel;
  }
}
