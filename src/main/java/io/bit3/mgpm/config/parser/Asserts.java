package io.bit3.mgpm.config.parser;

import io.bit3.mgpm.config.InvalidConfigException;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Asserts {
  public static void assertNotNull(Object object, String message, Object... arguments) {
    if (null == object) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertNotEmpty(Object object, String message, Object... arguments) {
    if (null == object || "".equals(object.toString().trim())) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertIsString(Object object, String message, Object... arguments) {
    if (!(object instanceof String)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertIsBoolean(Object object, String message, Object... arguments) {
    if (!(object instanceof Boolean)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertIsList(Object object, String message, Object... arguments) {
    if (!(object instanceof List)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertIsMap(Object object, String message, Object... arguments) {
    if (!(object instanceof Map)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertStartsWith(String string, String prefix, String message, Object... arguments) {
    if (!string.startsWith(prefix)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertEndsWith(String string, String suffix, String message, Object... arguments) {
    if (!string.endsWith(suffix)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertPath(String string, String message, Object... arguments) {
    if (!Objects.equals(string, Paths.get(string).normalize().toString() + "/")) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }

  public static void assertMatch(String string, String regex, String message, Object... arguments) {
    if (!string.matches(regex)) {
      throw new InvalidConfigException(
          String.format(message, arguments)
      );
    }
  }
}
