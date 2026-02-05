package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsConfig;
import kr.codename.focuscript.core.workspace.ScriptManifest;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

public final class PaperFsConfig implements FsConfig {

    private final ScriptManifest manifest;
    private final YamlConfiguration raw;

    public PaperFsConfig(ScriptManifest manifest, YamlConfiguration raw) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.raw = Objects.requireNonNull(raw, "raw");
    }

    @Override
    public String getId() {
        return manifest.id();
    }

    @Override
    public String getName() {
        return manifest.name();
    }

    @Override
    public String getVersion() {
        return manifest.version();
    }

    @Override
    public int getApiVersion() {
        return manifest.api();
    }

    @Override
    public String getString(String path) {
        return raw.getString(path);
    }

    @Override
    public String getString(String path, String def) {
        return raw.getString(path, def);
    }

    @Override
    public int getInt(String path, int def) {
        return raw.getInt(path, def);
    }

    @Override
    public boolean getBoolean(String path, boolean def) {
        return raw.getBoolean(path, def);
    }

    @Override
    public java.util.List<String> getStringList(String path) {
        return raw.getStringList(path);
    }

    @Override
    public java.util.List<String> getStringList(String path, java.util.List<String> def) {
        java.util.List<String> list = raw.getStringList(path);
        if (list == null || list.isEmpty()) return def;
        return list;
    }
}
