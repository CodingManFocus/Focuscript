package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsStorage;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class PaperFsStorage implements FsStorage {
    private final Path filePath;
    private final PaperFsLogger log;
    private YamlConfiguration data;

    public PaperFsStorage(Path filePath, PaperFsLogger log) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.log = Objects.requireNonNull(log, "log");
        ensureParent();
        this.data = YamlConfiguration.loadConfiguration(filePath.toFile());
    }

    @Override
    public boolean contains(String key) {
        return data.contains(key);
    }

    @Override
    public String getString(String key) {
        return data.getString(key);
    }

    @Override
    public String getString(String key, String def) {
        return data.getString(key, def);
    }

    @Override
    public void setString(String key, String value) {
        data.set(key, value);
    }

    @Override
    public int getInt(String key, int def) {
        return data.getInt(key, def);
    }

    @Override
    public void setInt(String key, int value) {
        data.set(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return data.getBoolean(key, def);
    }

    @Override
    public void setBoolean(String key, boolean value) {
        data.set(key, value);
    }

    @Override
    public List<String> getStringList(String key) {
        return data.getStringList(key);
    }

    @Override
    public void setStringList(String key, List<String> value) {
        data.set(key, value);
    }

    @Override
    public void remove(String key) {
        data.set(key, null);
    }

    @Override
    public void save() {
        try {
            data.save(filePath.toFile());
        } catch (Exception e) {
            log.error("Storage save failed: " + filePath, e);
        }
    }

    @Override
    public void reload() {
        data = YamlConfiguration.loadConfiguration(filePath.toFile());
    }

    private void ensureParent() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            log.error("Storage directory create failed: " + filePath, e);
        }
    }
}
