package kr.codename.focuscript.core.workspace;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record ScriptWorkspace(
        Path root,
        Path scriptYml,
        ScriptManifest manifest,
        YamlConfiguration rawConfig
) {

    public ScriptWorkspace {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(scriptYml, "scriptYml");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(rawConfig, "rawConfig");
    }

    public static ScriptWorkspace load(Path workspaceRoot) throws IOException {
        Path script = workspaceRoot.resolve("script.yml");
        if (!Files.isRegularFile(script)) {
            throw new IOException("script.yml not found: " + script);
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(script.toFile());

        String id = yml.getString("id");
        if (id == null || id.isBlank()) {
            throw new IOException("script.yml missing required field: id");
        }
        String name = yml.getString("name", id);
        String version = yml.getString("version", "1.0.0");
        int api = yml.getInt("api", 1);
        String entry = yml.getString("entry", "src/main.fs");
        String load = yml.getString("load", "enable");
        boolean debug = yml.getBoolean("options.debug", false);
        java.util.List<String> depends = normalizeList(yml.getStringList("depends"));
        java.util.List<String> permissions = normalizeList(yml.getStringList("permissions"));
        java.util.List<String> commands = normalizeList(yml.getStringList("commands"));

        ScriptManifest manifest = new ScriptManifest(
                id,
                name,
                version,
                api,
                entry,
                load,
                debug,
                depends,
                permissions,
                commands
        );
        return new ScriptWorkspace(workspaceRoot, script, manifest, yml);
    }

    public String readScriptYmlText() throws IOException {
        return Files.readString(scriptYml, StandardCharsets.UTF_8);
    }

    private static java.util.List<String> normalizeList(java.util.List<String> input) {
        if (input == null || input.isEmpty()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String value : input) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return java.util.List.copyOf(out);
    }
}
