package io.bit3.mgpm.cli;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;

public class AnsiOutput {
  private final static char ESCAPE = '\u001b';
  private final static char BACKSPACE = '\u0008';
  private final static PrintStream out = System.out;
  private final static boolean DECORATED;
  private final static AnsiOutput instance;
  private final static String[] spinnerCharacters = new String[]{"|", "/", "-", "\\"};

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

    instance = new AnsiOutput();
  }

  private Map<String, String> activeWorkers = new LinkedHashMap<>();
  private int spinnerIndex = 0;
  private int writtenLines = 0;

  private AnsiOutput() {
  }

  public static AnsiOutput getInstance() {
    return instance;
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

  public synchronized void addActiveWorker(String label, String activity) {
    activeWorkers.put(label, activity);
  }

  public synchronized void removeActiveWorker(String label) {
    activeWorkers.remove(label);
  }

  public synchronized AnsiOutput rotateSpinner() {
    deleteSpinner();

    if (!DECORATED || activeWorkers.isEmpty()) {
      return this;
    }

    int localSpinnerIndex = spinnerIndex;
    for (Map.Entry<String, String> entry : activeWorkers.entrySet()) {
      String label = entry.getKey();
      String activity = entry.getValue();

      out.print(" (");
      out.print(spinnerCharacters[localSpinnerIndex]);
      out.print(") ");
      out.print(label);
      out.print(": ");
      out.println(activity);

      localSpinnerIndex = (localSpinnerIndex + 1) % spinnerCharacters.length;
      writtenLines++;
    }

    spinnerIndex = (spinnerIndex + 1) % spinnerCharacters.length;

    return this;
  }

  public synchronized AnsiOutput deleteSpinner() {
    if (!DECORATED || 0 == writtenLines) {
      return this;
    }

    // restore cursor position
    out.print(ESCAPE);
    out.print("[" + writtenLines + "A"); // cursor up

    // clear from cursor
    out.print(ESCAPE);
    out.print("[J"); // erase down

    writtenLines = 0;

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
