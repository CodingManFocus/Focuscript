package kr.codename.focuscript;

import kr.codename.focuscript.command.FocuscriptCommand;
import kr.codename.focuscript.command.FsCmdCommand;
import kr.codename.focuscript.core.ExampleWorkspaceSeeder;
import kr.codename.focuscript.core.ModuleManager;
import kr.codename.focuscript.logging.FocuscriptLogger;
import kr.codename.focuscript.webide.WebIdeManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focuscript main plugin.
 *
 * - Scans plugins/Focuscript/scripts/* for script.yml
 * - Compiles .fs -> Kotlin bytecode jar
 * - Loads modules with dedicated ClassLoader
 */
public final class FocuscriptPlugin extends JavaPlugin {

    private ModuleManager moduleManager;
    private Path extractedApiJar;
    private FocuscriptLogger log;

    // Lazy-started (disabled by default) web IDE server.
    private WebIdeManager webIdeManager;

    private FocuscriptLogger log() {
        if (log == null) {
            log = new FocuscriptLogger(getComponentLogger());
        }
        return log;
    }

    @Override
    public void onLoad() {
        FocuscriptLogger log = log();

        // 1) Environment checks
        if (Runtime.version().feature() < 21) {
            log.error("Focuscript requires JDK 21+. Current: " + Runtime.version());
            return;
        }


        // Best-effort Paper version check (do not hard fail on minor parsing issues)
        String mcVersion = Bukkit.getMinecraftVersion(); // ex: "1.21.11"
        if (!isAtLeastMinecraft121(mcVersion)) {
            log.error("Focuscript requires Paper/Minecraft 1.21+. Current: " + mcVersion);
            return;
        }

         // 2) Prepare directories
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.createDirectories(getDataFolder().toPath().resolve("scripts"));
            Files.createDirectories(getDataFolder().toPath().resolve("_runtime"));
            Files.createDirectories(getDataFolder().toPath().resolve("_build"));
        } catch (IOException e) {
            log.error("Failed to create data folders: " + e.getMessage());
        }


        // 3) Extract API jar (for compiler classpath)
        this.extractedApiJar = extractBundledApiJar();
    }

    @Override
    public void onEnable() {
        FocuscriptLogger log = log();

        if (Runtime.version().feature() < 21 || !isAtLeastMinecraft121(Bukkit.getMinecraftVersion())) {
            log.error("Environment not supported. Disabling Focuscript.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (extractedApiJar == null || !Files.exists(extractedApiJar)) {
            log.error("API jar missing. Focuscript cannot compile modules.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.moduleManager = new ModuleManager(this, extractedApiJar, log);

        // Command
        var cmd = getCommand("fs");
        if (cmd != null) {
            FocuscriptCommand executor = new FocuscriptCommand(this, moduleManager, moduleManager.getCommandRegistry());
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        var moduleCmd = getCommand("fscmd");
        if (moduleCmd != null) {
            FsCmdCommand executor = new FsCmdCommand(moduleManager, moduleManager.getCommandRegistry());
            moduleCmd.setExecutor(executor);
            moduleCmd.setTabCompleter(executor);
        }

        new ExampleWorkspaceSeeder(
                getDataFolder().toPath().resolve("scripts"),
                getDataFolder().toPath().resolve("focuscript.created"),
                log()
        ).seed();
        // Load modules
        moduleManager.loadAll();
    }

    @Override
    public void onDisable() {
        // Stop Web IDE first so it doesn't keep threads/ports.
        if (webIdeManager != null) {
            try {
                webIdeManager.stop();
            } catch (Throwable ignored) {
            }
        }
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
    }

    public Path getExtractedApiJar() {
        return extractedApiJar;
    }

    /**
     * Web IDE manager (disabled by default; starts only when explicitly requested).
     */
    public synchronized WebIdeManager getWebIdeManager() {
        if (webIdeManager == null) {
            if (moduleManager == null) {
                throw new IllegalStateException("ModuleManager is not initialized yet");
            }
            webIdeManager = new WebIdeManager(this, moduleManager, extractedApiJar, log());
        }
        return webIdeManager;
    }

    private Path extractBundledApiJar() {
        Path out = getDataFolder().toPath().resolve("_runtime").resolve("focuscript-api.jar");
        FocuscriptLogger log = log();
        try (InputStream in = getResource("focuscript-api.jar")) {
            if (in == null) {
                log.error("Resource focuscript-api.jar not found in plugin jar!");
                return null;
            }
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return out;
        } catch (IOException e) {
            log.error("Failed to extract focuscript-api.jar: " + e.getMessage());
            return null;
        }
    }


    private static boolean isAtLeastMinecraft121(String version) {
        // version format: "1.21", "1.21.4", "1.21.11"
        try {
            String[] parts = version.split("\\.");
            if (parts.length < 2) return false;
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            // For Minecraft, major is always 1 in modern versions.
            if (major != 1) return major > 1;
            return minor >= 21;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}