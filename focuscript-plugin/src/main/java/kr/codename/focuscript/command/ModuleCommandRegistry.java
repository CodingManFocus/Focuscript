package kr.codename.focuscript.command;

import kr.codename.focuscript.api.FsCommand;
import kr.codename.focuscript.api.FsCommandContext;
import kr.codename.focuscript.api.FsCommandHandler;
import kr.codename.focuscript.api.FsCommandSender;
import kr.codename.focuscript.api.FsText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ModuleCommandRegistry {
    private record Entry(String name, String permission, FsCommandHandler handler) {}

    private final Map<String, Map<String, Entry>> commandsByModule = new HashMap<>();

    public synchronized FsCommand register(String moduleId, String name, String permission, FsCommandHandler handler) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        String normalized = normalize(name);

        Map<String, Entry> moduleCommands = commandsByModule.computeIfAbsent(moduleId, id -> new HashMap<>());
        if (moduleCommands.containsKey(normalized)) {
            throw new IllegalArgumentException("Command already registered: " + normalized);
        }
        Entry entry = new Entry(normalized, normalizePermission(permission), handler);
        moduleCommands.put(normalized, entry);
        return new RegistryCommand(this, moduleId, normalized, entry.permission());
    }

    public synchronized void unregister(String moduleId, String name) {
        Map<String, Entry> moduleCommands = commandsByModule.get(moduleId);
        if (moduleCommands == null) return;
        moduleCommands.remove(normalize(name));
        if (moduleCommands.isEmpty()) {
            commandsByModule.remove(moduleId);
        }
    }

    public synchronized void unregisterAll(String moduleId) {
        commandsByModule.remove(moduleId);
    }

    public boolean dispatch(String moduleId, String name, FsCommandSender sender, String label, List<String> args) {
        Entry entry;
        synchronized (this) {
            Map<String, Entry> moduleCommands = commandsByModule.get(moduleId);
            if (moduleCommands == null) return false;
            entry = moduleCommands.get(normalize(name));
        }
        if (entry == null) return false;

        if (entry.permission() != null && !sender.hasPermission(entry.permission())) {
            sender.sendText(FsText.of("권한이 없습니다: " + entry.permission()));
            return true;
        }

        FsCommandContext context = new PaperFsCommandContext(sender, entry.name(), label, args);
        entry.handler().handle(context);
        return true;
    }

    public synchronized List<String> getCommandNames(String moduleId) {
        Map<String, Entry> moduleCommands = commandsByModule.get(moduleId);
        if (moduleCommands == null) return List.of();
        return new ArrayList<>(moduleCommands.keySet());
    }

    private static String normalize(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be empty");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalizePermission(String permission) {
        if (permission == null) return null;
        String trimmed = permission.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class RegistryCommand implements FsCommand {
        private final ModuleCommandRegistry registry;
        private final String moduleId;
        private final String name;
        private final String permission;

        private RegistryCommand(ModuleCommandRegistry registry, String moduleId, String name, String permission) {
            this.registry = registry;
            this.moduleId = moduleId;
            this.name = name;
            this.permission = permission;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPermission() {
            return permission;
        }

        @Override
        public void unregister() {
            registry.unregister(moduleId, name);
        }
    }
}
