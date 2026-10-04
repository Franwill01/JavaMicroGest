package sn.microgest.config;

import java.nio.file.Path;

public final class Settings {
  private Settings() {}

  public static Path home() {
    return Path.of(System.getProperty("microgest.home", ".")).toAbsolutePath().normalize();
  }
}
