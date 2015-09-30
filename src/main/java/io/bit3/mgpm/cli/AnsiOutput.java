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
  private final static char BACKSPACE = '\u0008';
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

  public AnsiOutput println() {
    out.println();

    return this;
  }

  public AnsiOutput println(String msg, Object... arguments) {
    print(msg, arguments);
    out.println();

    return this;
  }

  public AnsiOutput println(Color foregroundColor, String msg, Object... arguments) {
    print(foregroundColor, msg, arguments);
    out.println();

    return this;
  }

  public AnsiOutput print(String msg, Object... arguments) {
    out.printf(msg, arguments);

    return this;
  }

  public AnsiOutput print(Color foregroundColor, String msg, Object... arguments) {
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    print(msg, arguments);
    reset();

    return this;
  }

  public AnsiOutput print(Color foregroundColor, Color backgroundColor,
                    String msg, Object... arguments) {
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    color(foregroundColor.backgroundCode, backgroundColor.backgroundIntensity);
    print(msg, arguments);
    reset();

    return this;
  }

  public AnsiOutput print(int integer) {
    out.print(integer);

    return this;
  }

  public AnsiOutput print(Color foregroundColor, int integer) {
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    print(integer);
    reset();

    return this;
  }

  public AnsiOutput print(Color foregroundColor, Color backgroundColor, int integer) {
    color(foregroundColor.foregroundCode, foregroundColor.foregroundIntensity);
    color(backgroundColor.backgroundCode, backgroundColor.backgroundIntensity);
    print(integer);
    reset();

    return this;
  }

  public AnsiOutput delete() {
    out.write(BACKSPACE);

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
}
