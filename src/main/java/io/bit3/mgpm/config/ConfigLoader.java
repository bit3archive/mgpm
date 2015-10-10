package io.bit3.mgpm.config;

import io.bit3.mgpm.config.parser.ConfigConstructor;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class ConfigLoader {
  public void load(Config config) throws FileNotFoundException {
    load(config, new File("mgpm.yml"));
  }

  public void load(Config config, File file) throws FileNotFoundException {
    if (!file.exists()) {
      throw new FileNotFoundException("Could not find configuration file " + file.getPath());
    }

    if (!file.isFile()) {
      throw new InvalidConfigException(
          String.format("Config path \"%s\" is not a file", file.getPath())
      );
    }

    if (!file.canRead()) {
      throw new InvalidConfigException(
          String.format("Config path \"%s\" is not readable", file.getPath())
      );
    }

    Constructor constructor = new ConfigConstructor(config);

    Yaml yamlParser = new Yaml(constructor);
    try {
      yamlParser.load(new FileReader(file));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
