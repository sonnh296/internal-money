package com.mockbank.commons.dto.events;

public final class EventSchemaSupport {

  public static final String VERSION_1 = "1";

  private EventSchemaSupport() {
  }

  public static void requireVersion1(String schemaVersion, String eventName) {
    if (schemaVersion == null || schemaVersion.isBlank()) {
      return;
    }
    if (!VERSION_1.equals(schemaVersion)) {
      throw new IllegalArgumentException(
          "Unsupported schemaVersion for " + eventName + ": " + schemaVersion);
    }
  }
}
