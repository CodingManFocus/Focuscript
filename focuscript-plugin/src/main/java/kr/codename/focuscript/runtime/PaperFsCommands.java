package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsCommand;
import kr.codename.focuscript.api.FsCommandHandler;
import kr.codename.focuscript.api.FsCommands;
import kr.codename.focuscript.command.ModuleCommandRegistry;
import kr.codename.focuscript.core.workspace.ScriptManifest;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaperFsCommands implements FsCommands {
    private final ScriptManifest manifest;
    private final ModuleCommandRegistry registry;
    private final PaperFsLogger log;
    private final Set<String> allowedCommands;
    private final Set<String> allowedPermissions;

    public PaperFsCommands(ScriptManifest manifest, ModuleCommandRegistry registry, PaperFsLogger log) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.log = Objects.requireNonNull(log, "log");
        this.allowedCommands = manifest.commands().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.allowedPermissions = manifest.permissions().stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public FsCommand register(String name, FsCommandHandler handler) {
        return register(name, null, handler);
    }

    @Override
    public FsCommand register(String name, String permission, FsCommandHandler handler) {
        Objects.requireNonNull(handler, "handler");
        String normalized = normalizeName(name);
        String normalizedPerm = normalizePermission(permission);

        if (!allowedCommands.isEmpty() && !allowedCommands.contains(normalized)) {
            throw new IllegalArgumentException("Command not declared in script.yml: " + normalized);
        }
        if (normalizedPerm != null && !allowedPermissions.isEmpty() && !allowedPermissions.contains(normalizedPerm)) {
            throw new IllegalArgumentException("Permission not declared in script.yml: " + normalizedPerm);
        }

        try {
            return registry.register(manifest.id(), normalized, normalizedPerm, handler);
        } catch (IllegalArgumentException e) {
            log.error("Command register failed: " + normalized + " (" + e.getMessage() + ")");
            throw e;
        }
    }

    @Override
    public void unregisterAll() {
        registry.unregisterAll(manifest.id());
    }

    private static String normalizeName(String name) {
        if (name == null) throw new IllegalArgumentException("Command name is null");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Command name is empty");
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizePermission(String permission) {
        if (permission == null) return null;
        String trimmed = permission.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
