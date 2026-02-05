package kr.codename.focuscript.api;

/**
 * Read-only config view for modules.
 * Backed by script.yml (and can be extended later).
 */
public interface FsConfig {
    String getId();
    String getName();
    String getVersion();
    int getApiVersion();

    String getString(String path);
    String getString(String path, String def);

    int getInt(String path, int def);
    boolean getBoolean(String path, boolean def);

    java.util.List<String> getStringList(String path);
    java.util.List<String> getStringList(String path, java.util.List<String> def);
}
