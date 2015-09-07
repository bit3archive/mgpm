package io.bit3.mgpm.cli;

import io.bit3.mgpm.config.RepositoryConfig;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintStream;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;

public class AnsiOutput {
  private final static char ESCAPE = '\u001b';
  private final static PrintStream out = System.out;
  private final static boolean DECORATED;

  static {
    POSIX posix = POSIXFactory.getPOSIX();

    if ('\\' == File.separatorChar) {
      String ansicon = System.getenv("ANSICON");
      String conEmuAnsi = System.getenv("ConEmuANSI");
      DECORATED = null != ansicon && !ansicon.isEmpty() && !"false".equals(ansicon)
              || null != conEmuAnsi && "ON".equals(conEmuAnsi);
    } else {
      DECORATED = posix.isatty(FileDescriptor.out);
    }
  }

  private final int pad;
  private RepositoryConfig repositoryConfig;
  private boolean firstLine = true;
  private boolean newLine = true;

  public AnsiOutput(int pad) {
    this.pad = pad;
  }

  public RepositoryConfig getRepositoryConfig() {
    return repositoryConfig;
  }

  public void setRepositoryConfig(RepositoryConfig repositoryConfig) {
    this.repositoryConfig = repositoryConfig;
    firstLine = true;

    if (!newLine) {
      println();
    }
  }

  public AnsiOutput printRepository() {
    if (firstLine) {
      out.print(" * ");
      out.print(StringUtils.rightPad(repositoryConfig.getName(), pad));
      firstLine = false;
      newLine = false;
    } else if (newLine) {
      out.print(StringUtils.rightPad("", pad + 3));
      newLine = false;
    }

    return this;
  }

  public AnsiOutput println() {
    out.println();
    newLine = true;

    return this;
  }

  public AnsiOutput println(String msg, Object... arguments) {
    printRepository();
    print(msg, arguments);
    out.println();
    newLine = true;

    return this;
  }

  public AnsiOutput println(Color foregroundColor, String msg, Object... arguments) {
    printRepository();
    print(foregroundColor, msg, arguments);
    out.println();
    newLine = true;

    return this;
  }

  public AnsiOutput print(String msg, Object... arguments) {
    printRepository();
    out.printf(msg, arguments);

    return this;
  }

  public AnsiOutput print(Color foregroundColor, String msg, Object... arguments) {
    printRepository();
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    print(msg, arguments);
    reset();

    return this;
  }

  public AnsiOutput print(Color foregroundColor, Color backgroundColor,
                    String msg, Object... arguments) {
    printRepository();
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    color(foregroundColor.backgroundCode, backgroundColor.backgroundIntensity);
    print(msg, arguments);
    reset();

    return this;
  }

  public AnsiOutput print(int integer) {
    printRepository();
    out.print(integer);

    return this;
  }

  public AnsiOutput print(Color foregroundColor, int integer) {
    printRepository();
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    print(integer);
    reset();

    return this;
  }

  public AnsiOutput print(Color foregroundColor, Color backgroundColor, int integer) {
    printRepository();
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    color(backgroundColor.backgroundCode, backgroundColor.backgroundIntensity);
    print(integer);
    reset();

    return this;
  }

  private void color(int code, int intensity) {
    if (!DECORATED) {
      return;
    }

    out.print(ESCAPE);
    out.print('[');
    out.print(intensity);
    out.print(';');
    out.print(code);
    out.print('m');
  }

  private void reset() {
    if (!DECORATED) {
      return;
    }

    out.print(ESCAPE);
    out.print("[0m");
  }

  public enum Color {
    BLACK(30, 0, 40, 0),
    DARK_GRAY(30, 1, 40, 1),
    RED(31, 0, 41, 0),
    LIGHT_RED(31, 0, 41, 1),
    GREEN(32, 0, 42, 0),
    YELLOW(33, 0, 43, 0),
    BLUE(34, 0, 44, 0),
    MAGENTA(35, 0, 45, 0),
    CYAN(36, 0, 46, 0),
    LIGHT_GRAY(37, 0, 47, 0),
    WHITE(37, 1, 47, 1);

    private final int foregroundCode;
    private final int foregroundIntensity;
    private final int backgroundCode;
    private final int backgroundIntensity;

    Color(int foregroundCode, int foregroundIntensity, int backgroundCode, int backgroundIntensity) {
      this.foregroundCode = foregroundCode;
      this.foregroundIntensity = foregroundIntensity;
      this.backgroundCode = backgroundCode;
      this.backgroundIntensity = backgroundIntensity;
    }
  }
}
