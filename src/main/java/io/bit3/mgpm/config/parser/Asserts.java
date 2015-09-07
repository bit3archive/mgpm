package io.bit3.mgpm.config.parser;

import io.bit3.mgpm.config.InvalidConfigException;

import java.util.List;
import java.util.Map;

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
}
