package io.bit3.mgpm.cmd;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class OptionsFactory {
  public static final char HELP_OPT = 'h';
  public static final String HELP_LONG_OPT = "help";

  public static final char CONFIG_OPT = 'c';
  public static final String CONFIG_LONG_OPT = "config";

  public static final char INIT_OPT = 'i';
  public static final String INIT_LONG_OPT = "init";

  public static final char STAT_OPT = 's';
  public static final String STAT_LONG_OPT = "stat";

  public static final char UPDATE_OPT = 'u';
  public static final String UPDATE_LONG_OPT = "update";

  public static final char GUI_OPT = 'g';
  public static final String GUI_LONG_OPT = "gui";

  public static final char QUIET_OPT = 'q';

  public static final char VERBOSE_OPT = 'v';

  public static final String VERY_VERBOSE_OPT = "vv";

  public Options create() {
    Options options = new Options();

    options.addOption(createHelpOption());
    options.addOption(createConfigOption());
    options.addOption(createInitOption());
    options.addOption(createStatOption());
    options.addOption(createUpdateOption());
    options.addOption(createGuiOption());
    options.addOption(createQuietOption());
    options.addOption(createVerboseOption());
    options.addOption(createVeryVerboseOption());

    return options;
  }

  private Option createHelpOption() {
    return new Option(
        Character.toString(HELP_OPT),
        HELP_LONG_OPT,
        false,
        "Show this help."
    );
  }

  private Option createConfigOption() {
    Option option = new Option(
        Character.toString(CONFIG_OPT),
        CONFIG_LONG_OPT,
        true,
        "The config file to use (default: mgpm.yml in your current directory)."
    );
    option.setArgName("path");
    return option;
  }

  private Option createInitOption() {
    return new Option(
        Character.toString(INIT_OPT),
        INIT_LONG_OPT,
        false,
        "Init all (missing) repositories."
    );
  }

  private Option createStatOption() {
    return new Option(
        Character.toString(STAT_OPT),
        STAT_LONG_OPT,
        false,
        "Show stats of all repositories."
    );
  }

  private Option createUpdateOption() {
    return new Option(
        Character.toString(UPDATE_OPT),
        UPDATE_LONG_OPT,
        false,
        "Update all repositories."
    );
  }

  private Option createGuiOption() {
    return new Option(
        Character.toString(GUI_OPT),
        GUI_LONG_OPT,
        false,
        "Show GUI."
    );
  }

  private Option createQuietOption() {
    return new Option(
        Character.toString(QUIET_OPT),
        null,
        false,
        "Be quiet."
    );
  }

  private Option createVerboseOption() {
    return new Option(
        Character.toString(VERBOSE_OPT),
        null,
        false,
        "Be verbose."
    );
  }

  private Option createVeryVerboseOption() {
    return new Option(
        VERY_VERBOSE_OPT,
        null,
        false,
        "Be very verbose."
    );
  }
}
