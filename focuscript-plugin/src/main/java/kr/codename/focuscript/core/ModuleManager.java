package kr.codename.focuscript.core;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.command.ModuleCommandRegistry;
import kr.codename.focuscript.core.bridge.PaperEventBridge;
import kr.codename.focuscript.core.compiler.FsCompiler;
import kr.codename.focuscript.core.compiler.KotlinCompilationException;
import kr.codename.focuscript.core.loader.ModuleClassLoader;
import kr.codename.focuscript.core.workspace.ScriptManifest;
import kr.codename.focuscript.core.workspace.ScriptWorkspace;
import kr.codename.focuscript.runtime.PaperFsContext;
import kr.codename.focuscript.api.FsModule;
import org.bukkit.Bukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModuleManager {

    private final FocuscriptPlugin plugin;
    private final Path apiJarPath;

    private final FsCompiler compiler;
    private final PaperEventBridge eventBridge;
    private final ModuleCommandRegistry commandRegistry;
    private final kr.codename.focuscript.logging.FocuscriptLogger log;

    private final List<LoadedModule> loadedModules = new CopyOnWriteArrayList<>();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final Map<String, CompiledWorkspace> pendingCompiled = new HashMap<>();

    public ModuleManager(FocuscriptPlugin plugin, Path apiJarPath, kr.codename.focuscript.logging.FocuscriptLogger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.apiJarPath = Objects.requireNonNull(apiJarPath, "apiJarPath");
        this.log = Objects.requireNonNull(log, "log");

        this.eventBridge = new PaperEventBridge(plugin, log);
        Bukkit.getPluginManager().registerEvents(eventBridge, plugin);

        this.commandRegistry = new ModuleCommandRegistry();
        Path buildRoot = plugin.getDataFolder().toPath().resolve("_build");
        this.compiler = new FsCompiler(plugin, apiJarPath, buildRoot, log);
    }

    public int loadAll() {
        int generation = loadGeneration.incrementAndGet();
        pendingCompiled.clear();
        disableAll(); // ensure clean slate

        Path workspacesRoot = plugin.getDataFolder().toPath().resolve("scripts");
        if (!Files.isDirectory(workspacesRoot)) {
            log.warn("Workspaces folder does not exist: " + workspacesRoot);
            return 0;
        }

        List<Path> workspaceDirs = new ArrayList<>();
        try (var stream = Files.list(workspacesRoot)) {
            stream.filter(Files::isDirectory).forEach(workspaceDirs::add);
        } catch (Exception e) {
            log.error("Failed to list workspaces: " + e.getMessage(), e);
            return 0;
        }

        workspaceDirs.sort(Comparator.comparing(Path::getFileName));

        Map<String, ScriptWorkspace> workspacesById = new LinkedHashMap<>();
        for (Path wsDir : workspaceDirs) {
            Path scriptYml = wsDir.resolve("script.yml");
            if (!Files.isRegularFile(scriptYml)) continue;

            try {
                ScriptWorkspace ws = ScriptWorkspace.load(wsDir);
                ScriptManifest manifest = ws.manifest();

                if (!"enable".equalsIgnoreCase(manifest.load())) {
                    log.info("Skipping workspace " + manifest.id() + " (load=" + manifest.load() + ")");
                    continue;
                }

                if (workspacesById.containsKey(manifest.id())) {
                    log.error("Duplicate module id in workspaces: " + manifest.id());
                    continue;
                }

                workspacesById.put(manifest.id(), ws);
            } catch (Throwable t) {
                String msg = t.getMessage();
                log.error(
                        "Failed to read workspace at " + wsDir + ": "
                                + t.getClass().getSimpleName()
                                + (msg == null || msg.isBlank() ? "" : ": " + msg),
                        t
                );
            }
        }

        Map<String, ScriptWorkspace> validWorkspaces = new LinkedHashMap<>();
        for (Map.Entry<String, ScriptWorkspace> entry : workspacesById.entrySet()) {
            ScriptWorkspace ws = entry.getValue();
            ScriptManifest manifest = ws.manifest();

            List<String> missing = new ArrayList<>();
            for (String dep : manifest.depends()) {
                if (!workspacesById.containsKey(dep)) {
                    missing.add(dep);
                }
            }
            if (!missing.isEmpty()) {
                log.error("Skipping module " + manifest.id() + ": missing dependencies " + missing);
                continue;
            }

            validWorkspaces.put(entry.getKey(), ws);
        }

        List<String> loadOrder = resolveLoadOrder(validWorkspaces);
        Set<String> cyclic = new LinkedHashSet<>(validWorkspaces.keySet());
        cyclic.removeAll(loadOrder);
        if (!cyclic.isEmpty()) {
            log.error("Unresolved module dependencies (cycle): " + cyclic);
        }

        int scheduled = 0;
        for (String id : loadOrder) {
            ScriptWorkspace ws = validWorkspaces.get(id);
            if (ws == null) continue;
            scheduled++;
            scheduleCompileAsync(ws, generation);
        }

        log.info("Focuscript: queued " + scheduled + " module(s) for async compilation.");
        return scheduled;
    }

    private List<String> resolveLoadOrder(Map<String, ScriptWorkspace> workspaces) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (String id : workspaces.keySet()) {
            indegree.put(id, 0);
        }

        for (Map.Entry<String, ScriptWorkspace> entry : workspaces.entrySet()) {
            String id = entry.getKey();
            ScriptManifest manifest = entry.getValue().manifest();
            for (String dep : manifest.depends()) {
                if (!workspaces.containsKey(dep)) continue;
                indegree.put(id, indegree.get(id) + 1);
                dependents.computeIfAbsent(dep, key -> new ArrayList<>()).add(id);
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            order.add(id);
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int value = indegree.get(dependent) - 1;
                indegree.put(dependent, value);
                if (value == 0) {
                    queue.add(dependent);
                }
            }
        }

        return order;
    }

    private void scheduleCompileAsync(ScriptWorkspace ws, int generation) {
        ScriptManifest manifest = ws.manifest();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (generation != loadGeneration.get()) return;

            Path moduleJar;
            try {
                moduleJar = compiler.compileIfNeeded(ws);
            } catch (Throwable t) {
                String msg = t.getMessage();
                log.error(
                        "Failed to compile workspace " + manifest.id() + ": "
                                + t.getClass().getSimpleName()
                                + (msg == null || msg.isBlank() ? "" : ": " + msg),
                        t
                );
                return;
            }

            if (generation != loadGeneration.get()) return;
            Bukkit.getScheduler().runTask(plugin, () -> onCompiled(ws, moduleJar, generation));
        });
    }

    private void onCompiled(ScriptWorkspace ws, Path moduleJar, int generation) {
        if (generation != loadGeneration.get()) return;

        ScriptManifest manifest = ws.manifest();
        if (!dependenciesLoaded(manifest)) {
            pendingCompiled.putIfAbsent(manifest.id(), new CompiledWorkspace(ws, moduleJar));
            return;
        }

        if (enableCompiledWorkspace(ws, moduleJar)) {
            tryEnablePending(generation);
        }
    }

    private void tryEnablePending(int generation) {
        if (generation != loadGeneration.get()) return;

        boolean progress;
        do {
            progress = false;
            Iterator<Map.Entry<String, CompiledWorkspace>> it = pendingCompiled.entrySet().iterator();
            while (it.hasNext()) {
                CompiledWorkspace compiled = it.next().getValue();
                if (!dependenciesLoaded(compiled.workspace().manifest())) {
                    continue;
                }
                it.remove();
                if (enableCompiledWorkspace(compiled.workspace(), compiled.moduleJar())) {
                    progress = true;
                }
            }
        } while (progress);
    }

    private boolean dependenciesLoaded(ScriptManifest manifest) {
        for (String dep : manifest.depends()) {
            if (!isModuleLoaded(dep)) return false;
        }
        return true;
    }

    private boolean isModuleLoaded(String moduleId) {
        for (LoadedModule lm : loadedModules) {
            if (lm.manifest().id().equals(moduleId)) {
                return true;
            }
        }
        return false;
    }

    private boolean enableCompiledWorkspace(ScriptWorkspace ws, Path moduleJar) {
        ScriptManifest manifest = ws.manifest();
        Path wsDir = ws.root();
        ModuleClassLoader cl = null;

        try {
            // Load module jar with restricted classloader
            cl = new ModuleClassLoader(
                    plugin,
                    manifest.id(),
                    moduleJar.toUri().toURL(),
                    ModuleClassLoader.class.getClassLoader() // parent: plugin's classloader
            );

            // Entrypoint: generated @file:JvmName("FocuscriptEntry") class with getFocuscriptModule()
            String entryClassName = compiler.getEntrypointClassName(manifest);
            Class<?> entryClass = cl.loadClass(entryClassName);
            var getter = entryClass.getDeclaredMethod("getFocuscriptModule");
            Object moduleObj = getter.invoke(null);
            if (!(moduleObj instanceof FsModule module)) {
                throw new IllegalStateException("focuscriptModule is not FsModule: " + moduleObj);
            }

            // Context per module
            PaperFsContext context = new PaperFsContext(
                    plugin,
                    manifest,
                    ws.rawConfig(),
                    eventBridge,
                    wsDir,
                    commandRegistry,
                    log
            );

            // Enable module (isolate exceptions)
            log.info("Enabling module: " + manifest.id() + " (" + manifest.name() + " v" + manifest.version() + ")");
            try {
                module.onEnable(context);
            } catch (Throwable t) {
                log.error("Module " + manifest.id() + " failed onEnable: " + t.getMessage(), t);
                // Cleanup
                context.closeAll();
                try { cl.close(); } catch (Exception ignored) {}
                return false;
            }

            loadedModules.add(new LoadedModule(manifest, wsDir, moduleJar, cl, module, context));
            return true;
        } catch (Throwable t) {
            String msg = t.getMessage();
            log.error(
                    "Failed to load workspace at " + wsDir + ": "
                            + t.getClass().getSimpleName()
                            + (msg == null || msg.isBlank() ? "" : ": " + msg),
                    t
            );
            if (cl != null) {
                try { cl.close(); } catch (Exception ignored) {}
            }
            return false;
        }
    }

    public int disableAll() {
        int disabled = 0;

        // Disable in reverse order
        List<LoadedModule> snapshot = new ArrayList<>(loadedModules);
        Collections.reverse(snapshot);

        for (LoadedModule lm : snapshot) {
            try {
                log.info("Disabling module: " + lm.manifest().id());
                try {
                    lm.module().onDisable();
                } catch (Throwable t) {
                    log.error("Module " + lm.manifest().id() + " failed onDisable: " + t.getMessage(), t);
                }

                // Always cleanup tracked resources
                lm.context().closeAll();

                // Close classloader (release jar file handle)
                try {
                    ClassLoader cl = lm.classLoader();
                    if (cl instanceof AutoCloseable ac) {
                        ac.close();
                    }
                } catch (Exception e) {
                    log.warn("Failed to close classloader for module " + lm.manifest().id() + ": " + e.getMessage());
                }

                logPotentialThreadLeaks(lm);
                disabled++;
            } catch (Throwable t) {
                log.error("Failed to disable module " + lm.manifest().id() + ": " + t.getMessage(), t);
            } finally {
                loadedModules.remove(lm);
            }
        }

        return disabled;
    }

    private record CompiledWorkspace(ScriptWorkspace workspace, Path moduleJar) {}

    private void logPotentialThreadLeaks(LoadedModule lm) {
        ClassLoader moduleCl = lm.classLoader();
        if (moduleCl == null) return;

        List<String> offenders = new ArrayList<>();
        int nonDaemon = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread == null || !thread.isAlive()) continue;
            if (thread.getContextClassLoader() != moduleCl) continue;
            if (!thread.isDaemon()) nonDaemon++;
            offenders.add(thread.getName() + "(id=" + thread.getId() + ", daemon=" + thread.isDaemon() + ")");
        }

        if (!offenders.isEmpty()) {
            log.warn(
                    "Possible thread leak after disabling module "
                            + lm.manifest().id()
                            + ": "
                            + offenders
            );
            if (nonDaemon > 0) {
                log.warn("Possible CPU leak: module " + lm.manifest().id() + " has " + nonDaemon + " non-daemon thread(s)");
            }
        }
    }

    public List<LoadedModule> getLoadedModules() {
        return List.copyOf(loadedModules);
    }

    /**
     * Reloads a single module/workspace by its manifest id.
     *
     * <p>This method is intentionally <b>non-blocking</b> and designed for remote tooling
     * (e.g. Web IDE). Heavy Kotlin compilation is done asynchronously, while Bukkit interactions
     * (disable/enable) run on the main thread.</p>
     *
     * <p>If {@code script.yml}'s {@code load} is not {@code enable}, this will only disable the
     * currently loaded module (if any) and will not enable it again.</p>
     */
    public void reloadModuleAsync(String moduleId, java.util.function.Consumer<ReloadResult> callback) {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(callback, "callback");

        // Capture current generation. If a full /fs reload happens, generation changes and this job is ignored.
        final int generation = loadGeneration.get();

        // Resolve workspace from disk first (does not touch Bukkit).
        final ScriptWorkspace workspace;
        try {
            workspace = findWorkspaceById(moduleId);
        } catch (Exception e) {
            callback.accept(new ReloadResult(false, "Failed to read workspace: " + e.getMessage()));
            return;
        }

        if (workspace == null) {
            callback.accept(new ReloadResult(false, "Workspace not found: " + moduleId));
            return;
        }

        // Always perform disable/enable on main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (generation != loadGeneration.get()) {
                callback.accept(new ReloadResult(false, "Cancelled (another reload started)"));
                return;
            }

            ScriptManifest manifest = workspace.manifest();

            // If load is not enable, just disable and exit.
            if (!"enable".equalsIgnoreCase(manifest.load())) {
                boolean disabled = disableModuleIfLoaded(moduleId);
                callback.accept(new ReloadResult(true, disabled
                        ? "Module disabled (load=" + manifest.load() + ")"
                        : "Module not loaded (load=" + manifest.load() + ")"));
                return;
            }

            // Respect declared dependencies: if missing, instruct to reload all.
            if (!dependenciesLoaded(manifest)) {
                callback.accept(new ReloadResult(false,
                        "Missing loaded dependencies for " + manifest.id() + ": " + manifest.depends() + " (try /fs reload)"));
                return;
            }

            // Disable current module first to release jar handles (important on Windows).
            disableModuleIfLoaded(moduleId);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (generation != loadGeneration.get()) {
                    callback.accept(new ReloadResult(false, "Cancelled (another reload started)"));
                    return;
                }

                final Path moduleJar;
                try {
                    moduleJar = compiler.compileIfNeeded(workspace);
                } catch (Throwable t) {
                    String msg = t.getMessage();
                    if (t instanceof KotlinCompilationException kce) {
                        List<String> lines = kce.getMessages();
                        if (lines != null && !lines.isEmpty()) {
                            int max = Math.min(lines.size(), 40);
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < max; i++) {
                                sb.append(lines.get(i)).append('\n');
                            }
                            if (lines.size() > max) {
                                sb.append("... (").append(lines.size() - max).append(" more)");
                            }
                            msg = sb.toString().trim();
                        }
                    }
                    log.error(
                            "Failed to compile workspace " + manifest.id() + ": "
                                    + t.getClass().getSimpleName()
                                    + (msg == null || msg.isBlank() ? "" : ": " + msg),
                            t
                    );
                    callback.accept(new ReloadResult(false,
                            "Compilation failed for " + manifest.id() + (msg == null || msg.isBlank() ? "" : ":\n" + msg)));
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (generation != loadGeneration.get()) {
                        callback.accept(new ReloadResult(false, "Cancelled (another reload started)"));
                        return;
                    }

                    boolean ok = enableCompiledWorkspace(workspace, moduleJar);
                    callback.accept(new ReloadResult(ok, ok
                            ? "Reloaded module: " + manifest.id()
                            : "Failed to enable module: " + manifest.id()));
                });
            });
        });
    }

    /** Result for {@link #reloadModuleAsync(String, java.util.function.Consumer)}. */
    public record ReloadResult(boolean success, String message) {}

    private ScriptWorkspace findWorkspaceById(String moduleId) throws Exception {
        Path workspacesRoot = plugin.getDataFolder().toPath().resolve("scripts");
        if (!Files.isDirectory(workspacesRoot)) return null;

        try (var stream = Files.list(workspacesRoot)) {
            for (Path wsDir : stream.filter(Files::isDirectory).toList()) {
                Path scriptYml = wsDir.resolve("script.yml");
                if (!Files.isRegularFile(scriptYml)) continue;
                try {
                    ScriptWorkspace ws = ScriptWorkspace.load(wsDir);
                    if (ws.manifest().id().equals(moduleId)) {
                        return ws;
                    }
                } catch (Throwable ignored) {
                    // Skip invalid workspace
                }
            }
        }
        return null;
    }

    private boolean disableModuleIfLoaded(String moduleId) {
        LoadedModule target = null;
        for (LoadedModule lm : loadedModules) {
            if (lm.manifest().id().equals(moduleId)) {
                target = lm;
                break;
            }
        }
        if (target == null) return false;

        try {
            log.info("Disabling module: " + target.manifest().id());
            try {
                target.module().onDisable();
            } catch (Throwable t) {
                log.error("Module " + target.manifest().id() + " failed onDisable: " + t.getMessage(), t);
            }

            // Always cleanup tracked resources
            target.context().closeAll();

            // Close classloader (release jar file handle)
            try {
                ClassLoader cl = target.classLoader();
                if (cl instanceof AutoCloseable ac) {
                    ac.close();
                }
            } catch (Exception e) {
                log.warn("Failed to close classloader for module " + target.manifest().id() + ": " + e.getMessage());
            }

            logPotentialThreadLeaks(target);
            return true;
        } catch (Throwable t) {
            log.error("Failed to disable module " + target.manifest().id() + ": " + t.getMessage(), t);
            return false;
        } finally {
            loadedModules.remove(target);
        }
    }

    public ModuleCommandRegistry getCommandRegistry() {
        return commandRegistry;
    }
}
