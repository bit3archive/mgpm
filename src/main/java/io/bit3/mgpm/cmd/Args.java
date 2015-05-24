package io.bit3.mgpm.cmd;

import org.slf4j.spi.LocationAwareLogger;

import java.io.File;

public class Args {
  private File config = null;
  private boolean doList = false;
  private boolean doInit = false;
  private boolean doStat = false;
  private boolean doUpdate = false;
  private boolean showGui = false;
  private String loggerLevel = "trace";

  public boolean hasConfig() {
    return null != config;
  }

  public File getConfig() {
    return config;
  }

  public void setConfig(File config) {
    this.config = config;
  }

  public boolean isDoList() {
    return doList;
  }

  public void setDoList(boolean doList) {
    this.doList = doList;
  }

  public boolean isDoInit() {
    return doInit;
  }

  public void setDoInit(boolean doInit) {
    this.doInit = doInit;
  }

  public boolean isDoStat() {
    return doStat;
  }

  public void setDoStat(boolean doStat) {
    this.doStat = doStat;
  }

  public boolean isDoUpdate() {
    return doUpdate;
  }

  public void setDoUpdate(boolean doUpdate) {
    this.doUpdate = doUpdate;
  }

  public boolean isShowGui() {
    return showGui;
  }

  public void setShowGui(boolean showGui) {
    this.showGui = showGui;
  }

  public String getLoggerLevel() {
    return loggerLevel;
  }

  public void setLoggerLevel(String loggerLevel) {
    this.loggerLevel = loggerLevel;
  }
}
