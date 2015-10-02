package io.bit3.mgpm;

import io.bit3.mgpm.cli.CliApplication;
import io.bit3.mgpm.cmd.Args;
import io.bit3.mgpm.cmd.ArgsLoader;
import io.bit3.mgpm.cmd.LogLevel;
import io.bit3.mgpm.config.Config;
import io.bit3.mgpm.config.ConfigLoader;
import io.bit3.mgpm.gui.GuiApplication;

public class App {
  private final ConfigLoader loader;
  private final Args args;
  private final Config config = new Config();

  public App(ConfigLoader loader, Args args) {
    this.loader = loader;
    this.args = args;

    this.init();
  }

  public static void main(String[] cliArguments) {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", LogLevel.TRACE.toString().toLowerCase());

    ArgsLoader argsLoader = new ArgsLoader();
    Args args = argsLoader.load(cliArguments);

    if (null == args) {
      return;
    }

    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", args.getLoggerLevel().toString().toLowerCase());
    System.setProperty("org.slf4j.simpleLogger.log.io.bit3.mgpm", args.getLoggerLevel().toString().toLowerCase());

    ConfigLoader configLoader = new ConfigLoader();
    App app = new App(configLoader, args);

    if (args.isShowGui()) {
      app.runGui();
    } else {
      app.runCli();
    }
  }

  public void init() {
    if (args.hasConfig()) {
      loader.load(config, args.getConfig());
    } else {
      loader.load(config);
    }
  }

  public void runGui() {
    GuiApplication guiApplication = new GuiApplication(args, config);
    guiApplication.run();
  }

  public void runCli() {
    CliApplication cliApplication = new CliApplication(args, config);
    cliApplication.run();
  }
}
