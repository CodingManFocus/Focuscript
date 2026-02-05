package kr.codename.focuscript.webide;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.core.ModuleManager;
import kr.codename.focuscript.core.workspace.ScriptWorkspace;
import kr.codename.focuscript.logging.FocuscriptLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Lazy-started Web IDE server.
 *
 * <p>Design goals:</p>
 * <ul>
 *   <li><b>Disabled by default</b>: no server is started unless explicitly requested.</li>
 *   <li><b>Low impact</b>: uses a small daemon thread pool and avoids Bukkit main-thread work except when
 *       reloading modules.</li>
 *   <li><b>API awareness</b>: exposes an API index generated from the extracted focuscript-api.jar in
 *       plugins/Focuscript/_runtime.</li>
 * </ul>
 */
public final class WebIdeManager {

    private static final String STATIC_ROOT = "webide/";
    private static final int STATIC_FILE_LIMIT = 10 * 1024 * 1024;

    private final FocuscriptPlugin plugin;
    private final ModuleManager moduleManager;
    private final Path apiJarPath;
    private final FocuscriptLogger log;

    private volatile HttpServer server;
    private volatile ExecutorService executor;
    private volatile String token;
    private volatile int port;
    private volatile String bindHost;
    private volatile Instant startedAt;

    // Jobs for async operations triggered from the Web UI
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicLong jobSeq = new AtomicLong();

    // Cached API index JSON (built on demand)
    private volatile String cachedApiIndexJson;
    private volatile long cachedApiIndexMtime;


    public WebIdeManager(FocuscriptPlugin plugin, ModuleManager moduleManager, Path apiJarPath, FocuscriptLogger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.moduleManager = Objects.requireNonNull(moduleManager, "moduleManager");
        this.apiJarPath = Objects.requireNonNull(apiJarPath, "apiJarPath");
        this.log = Objects.requireNonNull(log, "log");
    }

    public boolean isRunning() {
        return server != null;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token;
    }

    public String getBindHost() {
        return bindHost;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * Starts the Web IDE if it is not running and sends a URL to the sender.
     *
     * <p>This method returns immediately and performs networking work asynchronously.</p>
     */
    public void openAsync(CommandSender sender, String bindHost, int requestedPort) {
        Objects.requireNonNull(sender, "sender");
        String bind = (bindHost == null || bindHost.isBlank()) ? "0.0.0.0" : bindHost.trim();
        int port = requestedPort <= 0 ? 17777 : requestedPort;

        if (isRunning()) {
            sendOpenMessage(sender);
            return;
        }

        sender.sendMessage("\u00a7e[Focuscript] Starting Web IDE...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            StartResult result;
            try {
                result = startInternal(bind, port);
            } catch (Throwable t) {
                String msg = t.getMessage();
                log.error("Failed to start Web IDE: " + (msg == null ? t.getClass().getSimpleName() : msg), t);
                StartResult fail = StartResult.failed(msg == null ? t.getClass().getSimpleName() : msg);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("\u00a7c[Focuscript] Web IDE start failed: " + fail.message());
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!result.success()) {
                    sender.sendMessage("\u00a7c[Focuscript] Web IDE start failed: " + result.message());
                    return;
                }
                sendOpenMessage(sender);
            });
        });
    }

    public synchronized void stop() {
        HttpServer s = this.server;
        this.server = null;

        if (s != null) {
            try {
                s.stop(0);
            } catch (Throwable ignored) {
            }
        }

        ExecutorService ex = this.executor;
        this.executor = null;
        if (ex != null) {
            try {
                ex.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
    }

    private synchronized StartResult startInternal(String bind, int requestedPort) throws IOException {
        if (this.server != null) {
            return StartResult.ok();
        }

        if (!Files.isRegularFile(apiJarPath)) {
            return StartResult.failed("API jar not found: " + apiJarPath);
        }

        String newToken = generateToken();

        InetAddress bindAddr;
        try {
            bindAddr = InetAddress.getByName(bind);
        } catch (Exception e) {
            return StartResult.failed("Invalid bind host: " + bind);
        }

        HttpServer http;
        try {
            http = HttpServer.create(new InetSocketAddress(bindAddr, requestedPort), 0);
        } catch (IOException bindFail) {
            // Fallback: choose an ephemeral port
            http = HttpServer.create(new InetSocketAddress(bindAddr, 0), 0);
        }

        int actualPort = http.getAddress().getPort();

        ExecutorService pool = Executors.newFixedThreadPool(
                4,
                new DaemonThreadFactory("Focuscript-WebIDE")
        );

        http.setExecutor(pool);
        http.createContext("/", new StaticHandler());
        http.createContext("/api", new ApiHandler());
        http.start();

        this.server = http;
        this.executor = pool;
        this.token = newToken;
        this.port = actualPort;
        this.bindHost = bind;
        this.startedAt = Instant.now();

        log.info("Web IDE started on " + bind + ":" + actualPort);
        return StartResult.ok();
    }

    private void sendOpenMessage(CommandSender sender) {
        if (!isRunning()) {
            sender.sendMessage("\u00a7c[Focuscript] Web IDE is not running.");
            return;
        }

        String local = "http://localhost:" + port + "/?token=" + token;
        String lanIp = guessLanAddress();
        String lan = lanIp == null ? null : ("http://" + lanIp + ":" + port + "/?token=" + token);

        sender.sendMessage("\u00a7a[Focuscript] Web IDE is running.");
        sender.sendMessage("\u00a77- Local: \u00a7f" + local);
        if (lan != null) {
            sender.sendMessage("\u00a77- LAN:   \u00a7f" + lan);
        }
        if ("0.0.0.0".equals(bindHost) || "::".equals(bindHost)) {
            sender.sendMessage("\u00a76Note: bound to all interfaces (" + bindHost + "). Keep the token private and use a firewall.");
        } else {
            sender.sendMessage("\u00a77(bound to " + bindHost + ")");
        }
    }

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                String rel = normalizeStaticPath(path);
                if (rel == null) {
                    send(exchange, 404, "text/plain; charset=utf-8", "Not Found");
                    return;
                }

                String resourcePath = STATIC_ROOT + rel;
                byte[] body = readStaticResource(resourcePath);
                if (body == null) {
                    send(exchange, 404, "text/plain; charset=utf-8", "Not Found");
                    return;
                }

                send(exchange, 200, contentTypeForPath(rel), body);
            } catch (Throwable t) {
                send(exchange, 500, "text/plain; charset=utf-8", "Internal Server Error");
            }
        }
    }

    private final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String sub = path == null ? "" : path.substring("/api".length());
                if (sub.isEmpty()) sub = "/";
                String method = exchange.getRequestMethod();

                Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
                if (!checkAuth(exchange, q)) {
                    return;
                }

                switch (sub) {
                    case "/status" -> {
                        if (!"GET".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        sendJson(exchange, 200, buildStatusJson());
                    }
                    case "/workspaces" -> {
                        if (!"GET".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        sendJson(exchange, 200, buildWorkspacesJson());
                    }
                    case "/files" -> {
                        if (!"GET".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        String ws = q.get("ws");
                        if (ws == null || ws.isBlank()) {
                            sendJson(exchange, 400, jsonError("missing_ws"));
                            return;
                        }
                        sendJson(exchange, 200, buildFilesJson(ws));
                    }
                    case "/file" -> {
                        String ws = q.get("ws");
                        String rel = q.get("path");
                        if (ws == null || ws.isBlank() || rel == null || rel.isBlank()) {
                            sendJson(exchange, 400, jsonError("missing_ws_or_path"));
                            return;
                        }
                        if ("GET".equalsIgnoreCase(method)) {
                            handleReadFile(exchange, ws, rel);
                            return;
                        }
                        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                            handleWriteFile(exchange, ws, rel);
                            return;
                        }
                        sendJson(exchange, 405, jsonError("method_not_allowed"));
                    }
                    case "/newFile" -> {
                        if (!"POST".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        String ws = q.get("ws");
                        String rel = q.get("path");
                        if (ws == null || ws.isBlank() || rel == null || rel.isBlank()) {
                            sendJson(exchange, 400, jsonError("missing_ws_or_path"));
                            return;
                        }
                        handleNewFile(exchange, ws, rel);
                    }
                    case "/newWorkspace" -> {
                        if (!"POST".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        String id = q.get("id");
                        if (id == null || id.isBlank()) {
                            sendJson(exchange, 400, jsonError("missing_id"));
                            return;
                        }
                        String name = q.get("name");
                        String version = q.get("version");
                        String load = q.get("load");
                        handleNewWorkspace(exchange, id, name, version, load);
                    }
                    case "/reload" -> {
                        if (!"POST".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        String ws = q.get("ws");
                        if (ws == null || ws.isBlank()) {
                            sendJson(exchange, 400, jsonError("missing_ws"));
                            return;
                        }
                        handleReloadWorkspace(exchange, ws);
                    }
                    case "/reloadAll" -> {
                        if (!"POST".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        handleReloadAll(exchange);
                    }
                    case "/job" -> {
                        if (!"GET".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        String id = q.get("id");
                        if (id == null || id.isBlank()) {
                            sendJson(exchange, 400, jsonError("missing_id"));
                            return;
                        }
                        sendJson(exchange, 200, buildJobJson(id));
                    }
                    case "/apiIndex" -> {
                        if (!"GET".equalsIgnoreCase(method)) {
                            sendJson(exchange, 405, jsonError("method_not_allowed"));
                            return;
                        }
                        sendJson(exchange, 200, getApiIndexJson());
                    }
                    default -> sendJson(exchange, 404, jsonError("not_found"));
                }
            } catch (Throwable t) {
                sendJson(exchange, 500, jsonError("internal_error"));
            }
        }

        private void handleReadFile(HttpExchange exchange, String wsId, String relPath) throws IOException {
            Path wsRoot = resolveWorkspaceRoot(wsId);
            if (wsRoot == null) {
                sendJson(exchange, 404, jsonError("workspace_not_found"));
                return;
            }
            Path file = safeResolve(wsRoot, relPath);
            if (file == null) {
                sendJson(exchange, 400, jsonError("invalid_path"));
                return;
            }
            if (!isSymlinkSafe(wsRoot, file)) {
                sendJson(exchange, 400, jsonError("invalid_path"));
                return;
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                sendJson(exchange, 404, jsonError("file_not_found"));
                return;
            }
            // Hard limit (avoid accidental huge downloads)
            long size = Files.size(file);
            if (size > 1024 * 1024) {
                sendJson(exchange, 413, jsonError("file_too_large"));
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            send(exchange, 200, "text/plain; charset=utf-8", bytes);
        }

        private void handleWriteFile(HttpExchange exchange, String wsId, String relPath) throws IOException {
            Path wsRoot = resolveWorkspaceRoot(wsId);
            if (wsRoot == null) {
                sendJson(exchange, 404, jsonError("workspace_not_found"));
                return;
            }
            Path file = safeResolve(wsRoot, relPath);
            if (file == null) {
                sendJson(exchange, 400, jsonError("invalid_path"));
                return;
            }
            if (!isSymlinkSafe(wsRoot, file)) {
                sendJson(exchange, 400, jsonError("invalid_path"));
                return;
            }
            byte[] body = readAll(exchange.getRequestBody(), 1024 * 1024);
            Files.createDirectories(file.getParent());
            Files.write(file, body);
            sendJson(exchange, 200, "{\"ok\":true}");
        }

        private void handleNewFile(HttpExchange exchange, String wsId, String relPath) throws IOException {
            Path wsRoot = resolveWorkspaceRoot(wsId);
            if (wsRoot == null) {
                sendJson(exchange, 404, jsonError("workspace_not_found"));
                return;
            }
            Path file = safeResolve(wsRoot, relPath);
            if (file == null) {
                sendJson(exchange, 400, jsonError("invalid_path"));
                return;
            }
            if (!isSymlinkSafe(wsRoot, file)) {
                sendJson(exchange, 400, jsonError("invalid_path"));
                return;
            }
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                Files.writeString(file, "", StandardCharsets.UTF_8);
            }
            sendJson(exchange, 200, "{\"ok\":true}");
        }

        private void handleNewWorkspace(HttpExchange exchange, String idRaw, String nameRaw, String versionRaw, String loadRaw) throws IOException {
            String id = (idRaw == null ? "" : idRaw.trim());
            if (!isValidModuleId(id)) {
                sendJson(exchange, 400, jsonError("invalid_id"));
                return;
            }

            String name = (nameRaw == null || nameRaw.isBlank()) ? id : nameRaw.trim();
            String version = (versionRaw == null || versionRaw.isBlank()) ? "1.0.0" : versionRaw.trim();
            String load = (loadRaw == null || loadRaw.isBlank()) ? "disable" : loadRaw.trim().toLowerCase(Locale.ROOT);
            if (!"enable".equals(load) && !"disable".equals(load)) {
                sendJson(exchange, 400, jsonError("invalid_load"));
                return;
            }

            Path scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts");
            Files.createDirectories(scriptsRoot);

            // Prevent duplicate module ids (even if folder name differs).
            if (resolveWorkspaceRoot(id) != null) {
                sendJson(exchange, 409, jsonError("duplicate_id"));
                return;
            }

            // Directory name: default to the id, sanitized to a safe folder name.
            String dirName = sanitizeFolderName(id);
            if (dirName.isBlank()) {
                sendJson(exchange, 400, jsonError("invalid_dir"));
                return;
            }

            Path wsDir = scriptsRoot.resolve(dirName).normalize();
            if (!wsDir.startsWith(scriptsRoot)) {
                sendJson(exchange, 400, jsonError("invalid_dir"));
                return;
            }
            if (Files.exists(wsDir)) {
                sendJson(exchange, 409, jsonError("dir_exists"));
                return;
            }

            // Create skeleton
            Files.createDirectories(wsDir.resolve("src"));

            String yml = String.join("\n",
                    "id: " + id,
                    "name: \"" + yamlEscape(name) + "\"",
                    "version: \"" + yamlEscape(version) + "\"",
                    "api: 1",
                    "entry: src/main.fs",
                    "load: " + load,
                    "depends: []",
                    "permissions: []",
                    "commands: []",
                    "options:",
                    "  debug: false",
                    ""
            );
            Files.writeString(wsDir.resolve("script.yml"), yml, StandardCharsets.UTF_8);

            String main = String.join("\n",
                    "module {",
                    "  log.info(\"" + kotlinEscape(id) + " module enabled\")",
                    "  onDisable {",
                    "    log.info(\"" + kotlinEscape(id) + " module disabled\")",
                    "  }",
                    "}",
                    ""
            );
            Files.writeString(wsDir.resolve("src").resolve("main.fs"), main, StandardCharsets.UTF_8);

            sendJson(exchange, 200, "{\"ok\":true,\"id\":\"" + jsonEscape(id) + "\",\"dir\":\"" + jsonEscape(dirName) + "\"}");
        }

        private void handleReloadWorkspace(HttpExchange exchange, String wsId) throws IOException {
            String jobId = createJob("reload", wsId);
            Job job = jobs.get(jobId);
            if (job == null) {
                sendJson(exchange, 500, jsonError("job_create_failed"));
                return;
            }
            job.setStatus("running", "Reloading module: " + wsId);

            moduleManager.reloadModuleAsync(wsId, result -> {
                if (result.success()) {
                    job.setStatus("success", result.message());
                } else {
                    job.setStatus("error", result.message());
                }
            });

            sendJson(exchange, 200, "{\"jobId\":\"" + jsonEscape(jobId) + "\"}");
        }

        private void handleReloadAll(HttpExchange exchange) throws IOException {
            String jobId = createJob("reloadAll", "*");
            Job job = jobs.get(jobId);
            if (job == null) {
                sendJson(exchange, 500, jsonError("job_create_failed"));
                return;
            }

            job.setStatus("running", "Disabling and reloading all modules...");

            // Disable/load must be on main thread. Compilation will run async afterwards (ModuleManager internal).
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    int disabled = moduleManager.disableAll();
                    int queued = moduleManager.loadAll();
                    job.setStatus("success", "Reload queued. disabled=" + disabled + ", queued=" + queued);
                } catch (Throwable t) {
                    job.setStatus("error", "Reload failed: " + t.getMessage());
                }
            });

            sendJson(exchange, 200, "{\"jobId\":\"" + jsonEscape(jobId) + "\"}");
        }
    }

    private String buildStatusJson() {
        String v = plugin.getDescription().getVersion();
        int loaded = moduleManager.getLoadedModules().size();
        return "{" +
                "\"running\":" + isRunning() + "," +
                "\"pluginVersion\":\"" + jsonEscape(v) + "\"," +
                "\"bind\":\"" + jsonEscape(String.valueOf(bindHost)) + "\"," +
                "\"port\":" + port + "," +
                "\"startedAt\":\"" + jsonEscape(String.valueOf(startedAt)) + "\"," +
                "\"loadedModules\":" + loaded +
                "}";
    }

    private String buildWorkspacesJson() {
        Path scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts");
        if (!Files.isDirectory(scriptsRoot)) {
            return "[]";
        }

        List<WorkspaceInfo> list = new ArrayList<>();
        try (var stream = Files.list(scriptsRoot)) {
            for (Path wsDir : stream.filter(Files::isDirectory).toList()) {
                Path scriptYml = wsDir.resolve("script.yml");
                if (!Files.isRegularFile(scriptYml, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    ScriptWorkspace ws = ScriptWorkspace.load(wsDir);
                    boolean loaded = moduleManager.getLoadedModules().stream().anyMatch(m -> m.manifest().id().equals(ws.manifest().id()));
                    list.add(new WorkspaceInfo(ws.manifest().id(), ws.manifest().name(), ws.manifest().version(), ws.manifest().load(), wsDir.getFileName().toString(), loaded));
                } catch (Throwable ignored) {
                    // ignore invalid workspace
                }
            }
        } catch (Throwable t) {
            return "[]";
        }

        list.sort(Comparator.comparing(WorkspaceInfo::id));

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            WorkspaceInfo w = list.get(i);
            if (i > 0) sb.append(',');
            sb.append('{')
                    .append("\"id\":\"").append(jsonEscape(w.id())).append("\",")
                    .append("\"name\":\"").append(jsonEscape(w.name())).append("\",")
                    .append("\"version\":\"").append(jsonEscape(w.version())).append("\",")
                    .append("\"load\":\"").append(jsonEscape(w.load())).append("\",")
                    .append("\"dir\":\"").append(jsonEscape(w.dir())).append("\",")
                    .append("\"loaded\":").append(w.loaded())
                    .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildFilesJson(String wsId) {
        Path wsRoot = resolveWorkspaceRoot(wsId);
        if (wsRoot == null) {
            return "[]";
        }

        List<String> files = new ArrayList<>();
        try (var walk = Files.walk(wsRoot)) {
            // Do not follow symlinks while listing files. (Symlinks can be used for path escape attacks.)
            walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .limit(2000)
                    .forEach(p -> {
                        Path rel = wsRoot.relativize(p);
                        files.add(rel.toString().replace('\\', '/'));
                    });
        } catch (Throwable t) {
            return "[]";
        }

        files.sort(String::compareTo);

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < files.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsonEscape(files.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildJobJson(String id) {
        Job job = jobs.get(id);
        if (job == null) {
            return "{\"error\":\"not_found\"}";
        }
        return "{" +
                "\"id\":\"" + jsonEscape(job.id) + "\"," +
                "\"type\":\"" + jsonEscape(job.type) + "\"," +
                "\"target\":\"" + jsonEscape(job.target) + "\"," +
                "\"status\":\"" + jsonEscape(job.status) + "\"," +
                "\"message\":\"" + jsonEscape(job.message) + "\"," +
                "\"updatedAt\":\"" + jsonEscape(String.valueOf(job.updatedAt)) + "\"" +
                "}";
    }

    private String getApiIndexJson() {
        try {
            long mtime = Files.getLastModifiedTime(apiJarPath).toMillis();
            String cached = cachedApiIndexJson;
            if (cached != null && cachedApiIndexMtime == mtime) {
                return cached;
            }
            String built = buildApiIndexJson();
            cachedApiIndexJson = built;
            cachedApiIndexMtime = mtime;
            return built;
        } catch (Throwable t) {
            return "[]";
        }
    }

    private String buildApiIndexJson() throws IOException {
        if (!Files.isRegularFile(apiJarPath)) {
            return "[]";
        }

        record ApiClass(String name, String kind, List<String> methods, List<String> fields) {}

        List<ApiClass> classes = new ArrayList<>();

        try (JarFile jar = new JarFile(apiJarPath.toFile())) {
            List<String> classNames = new ArrayList<>();
            for (Enumeration<JarEntry> it = jar.entries(); it.hasMoreElements(); ) {
                JarEntry e = it.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class")) continue;
                if (n.contains("$")) continue; // ignore inner classes for MVP
                if (!n.startsWith("kr/codename/focuscript/api/")) continue;
                String cn = n.substring(0, n.length() - ".class".length()).replace('/', '.');
                classNames.add(cn);
            }

            classNames.sort(String::compareTo);

            try (var cl = new java.net.URLClassLoader(new java.net.URL[]{apiJarPath.toUri().toURL()}, null)) {
                for (String cn : classNames) {
                    try {
                        Class<?> c = cl.loadClass(cn);

                        String kind;
                        if (c.isInterface()) kind = "interface";
                        else if (c.isEnum()) kind = "enum";
                        else if (java.lang.reflect.Modifier.isAbstract(c.getModifiers())) kind = "abstract";
                        else kind = "class";

                        List<String> methods = new ArrayList<>();
                        for (var m : c.getDeclaredMethods()) {
                            if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
                            if (m.isSynthetic()) continue;
                            StringBuilder sig = new StringBuilder();
                            sig.append(simplifyType(m.getReturnType())).append(' ');
                            sig.append(m.getName()).append('(');
                            Class<?>[] params = m.getParameterTypes();
                            for (int i = 0; i < params.length; i++) {
                                if (i > 0) sig.append(", ");
                                sig.append(simplifyType(params[i]));
                            }
                            sig.append(')');
                            methods.add(sig.toString());
                        }
                        methods.sort(String::compareTo);

                        List<String> fields = new ArrayList<>();
                        for (var f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isPublic(f.getModifiers())) continue;
                            if (f.isSynthetic()) continue;
                            fields.add(simplifyType(f.getType()) + " " + f.getName());
                        }
                        fields.sort(String::compareTo);

                        classes.add(new ApiClass(cn, kind, methods, fields));
                    } catch (Throwable ignored) {
                        // Skip classes that fail to load
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < classes.size(); i++) {
            ApiClass c = classes.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"name\":\"").append(jsonEscape(c.name())).append("\",");
            sb.append("\"kind\":\"").append(jsonEscape(c.kind())).append("\",");
            sb.append("\"fields\":[");
            for (int j = 0; j < c.fields().size(); j++) {
                if (j > 0) sb.append(',');
                sb.append('"').append(jsonEscape(c.fields().get(j))).append('"');
            }
            sb.append("],");
            sb.append("\"methods\":[");
            for (int j = 0; j < c.methods().size(); j++) {
                if (j > 0) sb.append(',');
                sb.append('"').append(jsonEscape(c.methods().get(j))).append('"');
            }
            sb.append("]}");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String simplifyType(Class<?> type) {
        if (type == null) return "?";
        if (type.isArray()) {
            return simplifyType(type.getComponentType()) + "[]";
        }
        return type.getSimpleName();
    }

    private static Path safeResolve(Path root, String relPath) {
        if (relPath == null) return null;
        String trimmed = relPath.trim();
        if (trimmed.isEmpty()) return null;

        // Reject absolute paths
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) return null;
        if (trimmed.contains("..")) {
            // normalize check below, but quick fail for obvious traversal
        }

        Path resolved = root.resolve(trimmed).normalize();
        if (!resolved.startsWith(root)) return null;
        return resolved;
    }

    /**
     * Prevent path escape via symlinks.
     *
     * <p>{@link #safeResolve(Path, String)} prevents ".." traversal, but symlinks inside the workspace
     * could still point outside of the workspace root. This check rejects any request path that
     * traverses a symbolic link.</p>
     */
    private static boolean isSymlinkSafe(Path root, Path candidate) {
        if (root == null || candidate == null) return false;
        try {
            Path rel = root.relativize(candidate);
            Path cur = root;
            for (Path part : rel) {
                cur = cur.resolve(part);
                if (Files.exists(cur, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cur)) {
                    return false;
                }
            }

            // If the target already exists, also ensure its real path stays within the real root.
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                Path realRoot = root.toRealPath();
                Path realTarget = candidate.toRealPath();
                return realTarget.startsWith(realRoot);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidModuleId(String id) {
        if (id == null) return false;
        String s = id.trim();
        if (s.isEmpty() || s.length() > 48) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') continue;
            if (c >= 'A' && c <= 'Z') continue;
            if (c >= '0' && c <= '9') continue;
            if (c == '_' || c == '-' || c == '.') continue;
            return false;
        }
        return true;
    }

    private static String sanitizeFolderName(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok =
                    (c >= 'a' && c <= 'z')
                            || (c >= 'A' && c <= 'Z')
                            || (c >= '0' && c <= '9')
                            || c == '_' || c == '-' || c == '.';
            sb.append(ok ? c : '-');
            if (sb.length() >= 64) break;
        }
        String out = sb.toString();
        // Trim leading/trailing separators.
        out = out.replaceAll("^[\\-\\._]+", "").replaceAll("[\\-\\._]+$", "");
        return out;
    }

    private static String yamlEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String kotlinEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Path resolveWorkspaceRoot(String wsId) {
        Path scriptsRoot = plugin.getDataFolder().toPath().resolve("scripts");
        if (!Files.isDirectory(scriptsRoot)) return null;

        try (var stream = Files.list(scriptsRoot)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                Path scriptYml = dir.resolve("script.yml");
                if (!Files.isRegularFile(scriptYml, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    ScriptWorkspace ws = ScriptWorkspace.load(dir);
                    if (ws.manifest().id().equals(wsId)) {
                        return dir;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean checkAuth(HttpExchange exchange, Map<String, String> q) throws IOException {
        String t = token;
        if (t == null || t.isBlank()) {
            sendJson(exchange, 403, jsonError("no_token"));
            return false;
        }

        // Token is passed via URL query: /?token=... (similar to VS Code Live Share links).
        String qToken = q == null ? null : q.get("token");
        if (qToken != null && t.equals(qToken)) {
            return true;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        sendJson(exchange, 401, jsonError("unauthorized"));
        return false;
    }

    private String createJob(String type, String target) {
        String id = Long.toString(System.currentTimeMillis(), 36) + "-" + Long.toString(jobSeq.incrementAndGet(), 36);
        Job job = new Job(id, type, target);
        jobs.put(id, job);
        pruneJobs();
        return id;
    }

    private void pruneJobs() {
        final int maxJobs = 200;
        final long ttlMs = 30L * 60L * 1000L; // 30 minutes

        int size = jobs.size();
        if (size <= maxJobs) {
            // Still clean up very old jobs opportunistically.
            long now = System.currentTimeMillis();
            for (Job j : new ArrayList<>(jobs.values())) {
                long age = now - j.updatedAt.toEpochMilli();
                if (age > ttlMs) {
                    jobs.remove(j.id);
                }
            }
            return;
        }

        long now = System.currentTimeMillis();
        for (Job j : new ArrayList<>(jobs.values())) {
            long age = now - j.updatedAt.toEpochMilli();
            if (age > ttlMs) {
                jobs.remove(j.id);
            }
        }

        size = jobs.size();
        if (size <= maxJobs) return;

        // If still too many, drop the oldest jobs.
        List<Job> list = new ArrayList<>(jobs.values());
        list.sort(Comparator.comparing(o -> o.updatedAt));
        int remove = list.size() - maxJobs;
        for (int i = 0; i < remove; i++) {
            jobs.remove(list.get(i).id);
        }
    }

    private static final class Job {
        final String id;
        final String type;
        final String target;
        volatile String status;
        volatile String message;
        volatile Instant updatedAt;

        Job(String id, String type, String target) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.status = "queued";
            this.message = "";
            this.updatedAt = Instant.now();
        }

        void setStatus(String status, String message) {
            this.status = status;
            this.message = message == null ? "" : message;
            this.updatedAt = Instant.now();
        }
    }

    private record WorkspaceInfo(String id, String name, String version, String load, String dir, boolean loaded) {}

    private static String generateToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String guessLanAddress() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) return null;
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address)) continue;
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private byte[] readStaticResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                if ((STATIC_ROOT + "index.html").equals(path)) {
                    String fallback = "<html><body><h1>Focuscript Web IDE</h1><p>Resource missing: " + path + "</p></body></html>";
                    return fallback.getBytes(StandardCharsets.UTF_8);
                }
                return null;
            }
            return readAll(in, STATIC_FILE_LIMIT);
        } catch (Throwable t) {
            if ((STATIC_ROOT + "index.html").equals(path)) {
                String fallback = "<html><body><h1>Focuscript Web IDE</h1><p>Failed to read resource: " + path + "</p></body></html>";
                return fallback.getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }
    }

    private static byte[] readAll(InputStream in, int limitBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) >= 0) {
            if (n == 0) continue;
            total += n;
            if (total > limitBytes) {
                throw new IOException("Request body too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return out;
        String[] parts = rawQuery.split("&");
        for (String p : parts) {
            if (p.isEmpty()) continue;
            int idx = p.indexOf('=');
            String k = idx >= 0 ? p.substring(0, idx) : p;
            String v = idx >= 0 ? p.substring(idx + 1) : "";
            out.put(urlDecode(k), urlDecode(v));
        }
        return out;
    }

    private static String urlDecode(String s) {
        if (s == null) return "";
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        send(exchange, code, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        send(exchange, code, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int code, String contentType, byte[] body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");

        // Basic hardening headers. This is a remote tooling surface; keep it tight by default.
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("Permissions-Policy", "interest-cohort=()");

        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
            headers.set(
                    "Content-Security-Policy",
                    "default-src 'self'; "
                            + "img-src 'self' data:; "
                            + "style-src 'self' 'unsafe-inline'; "
                            + "script-src 'self' 'unsafe-inline'; "
                            + "connect-src 'self'; "
                            + "base-uri 'none'; "
                            + "frame-ancestors 'none'"
            );
        }

        exchange.sendResponseHeaders(code, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static String jsonError(String code) {
        return "{\"error\":\"" + jsonEscape(code) + "\"}";
    }

    private static String normalizeStaticPath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty() || "/".equals(path)) {
            return "index.html";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        path = path.replace('\\', '/');
        if (path.isEmpty()) {
            return "index.html";
        }
        if (path.endsWith("/")) {
            path = path + "index.html";
        }
        if (path.contains("..")) {
            return null;
        }
        if (path.startsWith("/")) {
            return null;
        }
        return path;
    }

    private static String contentTypeForPath(String path) {
        if (path == null) return "application/octet-stream";
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".json") || lower.endsWith(".map")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private record StartResult(boolean success, String message) {
        static StartResult ok() {
            return new StartResult(true, "ok");
        }

        static StartResult failed(String msg) {
            return new StartResult(false, msg == null ? "failed" : msg);
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicLong seq = new AtomicLong();

        private DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    }
}
