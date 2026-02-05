package kr.codename.focuscript.api;

import java.util.List;

public interface FsStorage {
    boolean contains(String key);

    String getString(String key);

    String getString(String key, String def);

    void setString(String key, String value);

    int getInt(String key, int def);

    void setInt(String key, int value);

    boolean getBoolean(String key, boolean def);

    void setBoolean(String key, boolean value);

    List<String> getStringList(String key);

    void setStringList(String key, List<String> value);

    void remove(String key);

    void save();

    void reload();
}
