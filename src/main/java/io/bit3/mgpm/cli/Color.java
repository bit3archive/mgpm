package io.bit3.mgpm.cli;

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

  public final int foregroundCode;
  public final int foregroundIntensity;
  public final int backgroundCode;
  public final int backgroundIntensity;

  Color(int foregroundCode, int foregroundIntensity, int backgroundCode, int backgroundIntensity) {
    this.foregroundCode = foregroundCode;
    this.foregroundIntensity = foregroundIntensity;
    this.backgroundCode = backgroundCode;
    this.backgroundIntensity = backgroundIntensity;
  }
}
