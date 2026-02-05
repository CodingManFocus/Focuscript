package kr.codename.focuscript.runtime;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.api.*;
import kr.codename.focuscript.core.bridge.PaperEventBridge;
import kr.codename.focuscript.core.workspace.ScriptManifest;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

/**
 * Per-module context implementation (Paper bridge).
 *
 * Holds module-scoped event/scheduler trackers for automatic cleanup.
 */
public final class PaperFsContext implements FsContext {

    private final PaperFsServer server;
    private final PaperFsEvents events;
    private final PaperFsScheduler scheduler;
    private final PaperFsLogger log;
    private final PaperFsConfig config;
    private final PaperFsCommands commands;
    private final PaperFsStorage storage;

    public PaperFsContext(
            FocuscriptPlugin plugin,
            ScriptManifest manifest,
            YamlConfiguration rawConfig,
            PaperEventBridge eventBridge,
            java.nio.file.Path workspaceRoot,
            kr.codename.focuscript.command.ModuleCommandRegistry commandRegistry,
            kr.codename.focuscript.logging.FocuscriptLogger pluginLog
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(rawConfig, "rawConfig");
        Objects.requireNonNull(eventBridge, "eventBridge");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(commandRegistry, "commandRegistry");
        Objects.requireNonNull(pluginLog, "pluginLog");

        this.log = new PaperFsLogger(pluginLog.raw(), manifest.id(), manifest.debug());
        this.config = new PaperFsConfig(manifest, rawConfig);
        this.scheduler = new PaperFsScheduler(plugin, manifest.id(), log);
        this.events = new PaperFsEvents(manifest.id(), eventBridge);
        this.server = new PaperFsServer(plugin, log);
        this.commands = new PaperFsCommands(manifest, commandRegistry, log);
        this.storage = new PaperFsStorage(workspaceRoot.resolve("data.yml"), log);
    }

    @Override
    public FsServer getServer() {
        return server;
    }

    @Override
    public FsEvents getEvents() {
        return events;
    }

    @Override
    public FsScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public FsLogger getLog() {
        return log;
    }

    @Override
    public FsConfig getConfig() {
        return config;
    }

    @Override
    public FsCommands getCommands() {
        return commands;
    }

    @Override
    public FsStorage getStorage() {
        return storage;
    }

    /**
     * Called by Focuscript runtime when module is disabled (even if module throws).
     */
    public void closeAll() {
        try {
            events.unsubscribeAll();
        } catch (Throwable ignored) {}

        try {
            scheduler.cancelAll();
        } catch (Throwable ignored) {}

        try {
            commands.unregisterAll();
        } catch (Throwable ignored) {}

        try {
            storage.save();
        } catch (Throwable ignored) {}
    }
}
