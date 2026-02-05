package kr.codename.focuscript.core.workspace;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Parsed script.yml manifest.
 */
public record ScriptManifest(
        String id,
        String name,
        String version,
        int api,
        String entry,   // relative path string (e.g. src/main.fs)
        String load,    // enable
        boolean debug,
        java.util.List<String> depends,
        java.util.List<String> permissions,
        java.util.List<String> commands
) {
    public ScriptManifest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(depends, "depends");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(commands, "commands");
    }

    public Path resolveEntry(Path workspaceRoot) {
        return workspaceRoot.resolve(entry);
    }
}
