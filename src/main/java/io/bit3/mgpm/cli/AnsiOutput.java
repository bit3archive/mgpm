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
    color(foregroundColor.foregroundCode);
    print(msg, arguments);
    reset();

    return this;
  }

  public AnsiOutput print(Color foregroundColor, Color backgroundColor,
                    String msg, Object... arguments) {
    printRepository();
    color(foregroundColor.foregroundCode);
    color(foregroundColor.backgroundCode);
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
    color(foregroundColor.foregroundCode);
    print(integer);
    reset();

    return this;
  }

  public AnsiOutput print(Color foregroundColor, Color backgroundColor, int integer) {
    printRepository();
    color(foregroundColor.foregroundCode);
    color(foregroundColor.backgroundCode);
    print(integer);
    reset();

    return this;
  }

  private void color(int code) {
    if (!DECORATED) {
      return;
    }

    out.print(ESCAPE);
    out.print('[');
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
    BLACK(30, 40),
    RED(31, 41),
    GREEN(32, 42),
    YELLOW(33, 43),
    BLUE(34, 44),
    MAGENTA(35, 45),
    CYAN(36, 46),
    WHITE(37, 47);

    private final int foregroundCode;
    private final int backgroundCode;

    Color(int foregroundCode, int backgroundCode) {
      this.foregroundCode = foregroundCode;
      this.backgroundCode = backgroundCode;
    }
  }
}
