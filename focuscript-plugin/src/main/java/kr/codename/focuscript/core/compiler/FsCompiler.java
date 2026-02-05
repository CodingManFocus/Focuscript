package kr.codename.focuscript.core.compiler;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.api.FsApi;
import kr.codename.focuscript.core.workspace.ScriptManifest;
import kr.codename.focuscript.core.workspace.ScriptWorkspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * .fs -> Kotlin source -> compile to module.jar (bytecode) with caching.
 */
public final class FsCompiler {

    /**
     * IMPORTANT:
     * Must match plugin.yml libraries version.
     */
    public static final String KOTLIN_COMPILER_VERSION = "2.2.20";

    private static final String MODULE_PACKAGE_BASE = "kr.codename.focuscript.modules";

    private final FocuscriptPlugin plugin;
    private final Path apiJarPath;
    private final Path buildRoot;
    private final KotlinCompilerInvoker kotlin;
    private final kr.codename.focuscript.logging.FocuscriptLogger log;

    public FsCompiler(FocuscriptPlugin plugin, Path apiJarPath, Path buildRoot, kr.codename.focuscript.logging.FocuscriptLogger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.apiJarPath = Objects.requireNonNull(apiJarPath, "apiJarPath");
        this.buildRoot = Objects.requireNonNull(buildRoot, "buildRoot");
        this.log = Objects.requireNonNull(log, "log");
        this.kotlin = new KotlinCompilerInvoker(log);
    }

    public String getModulePackage(ScriptManifest manifest) {
        return MODULE_PACKAGE_BASE + "." + sanitizeIdAsPackagePart(manifest.id());
    }

    public String getEntrypointClassName(ScriptManifest manifest) {
        // @file:JvmName("FocuscriptEntry")
        return getModulePackage(manifest) + ".FocuscriptEntry";
    }

    public Path compileIfNeeded(ScriptWorkspace workspace) throws Exception {
        ScriptManifest manifest = workspace.manifest();

        Path buildDir = buildRoot.resolve(sanitizeIdAsPath(manifest.id()));
        Path cacheDir = buildDir.resolve("cache");
        Path genSrcDir = buildDir.resolve("gen-src");
        Path outJar = buildDir.resolve("module.jar");

        Files.createDirectories(buildDir);
        Files.createDirectories(cacheDir);

        // Collect inputs
        List<Path> sources = collectFsSources(workspace.root());
        sources.sort(Comparator.comparing(Path::toString));

        String scriptYmlText = workspace.readScriptYmlText();

        String cacheKey = computeCacheKey(scriptYmlText, sources, manifest);

        Path cachedJar = cacheDir.resolve(cacheKey + ".jar");
        if (Files.isRegularFile(cachedJar)) {
            // cache hit
            Files.createDirectories(outJar.getParent());
            Files.copy(cachedJar, outJar, StandardCopyOption.REPLACE_EXISTING);
            if (manifest.debug()) {
                log.info("[" + manifest.id() + "] cache hit: " + cacheKey);
            }
            return outJar;
        }

        // (Re)generate kotlin sources
        if (Files.exists(genSrcDir)) {
            deleteRecursively(genSrcDir);
        }
        Files.createDirectories(genSrcDir);

        String modulePackage = getModulePackage(manifest);

        // 1) Prelude (module DSL + helpers)
        Path preludeKt = genSrcDir.resolve("__FocuscriptPrelude.kt");
        Files.writeString(preludeKt, KotlinSourceTemplates.prelude(modulePackage), StandardCharsets.UTF_8);

        // 2) Entry (converted from entry .fs)
        Path entryFs = manifest.resolveEntry(workspace.root());
        if (!Files.isRegularFile(entryFs)) {
            throw new IOException("Entry file not found: " + entryFs);
        }
        String entryText = Files.readString(entryFs, StandardCharsets.UTF_8);
        validateNoPackageOrImport(entryText, entryFs);

        if (!startsWithModuleCall(entryText)) {
            throw new IOException("Entry file must start with `module { ... }` (comments/whitespace allowed): " + entryFs);
        }

        Path entryKt = genSrcDir.resolve("__FocuscriptEntry.kt");
        Files.writeString(entryKt, KotlinSourceTemplates.entry(modulePackage, entryText), StandardCharsets.UTF_8);

        // 3) Other sources
        int idx = 0;
        for (Path fs : sources) {
            if (fs.equals(entryFs)) continue;
            String src = Files.readString(fs, StandardCharsets.UTF_8);
            validateNoPackageOrImport(src, fs);

            Path out = genSrcDir.resolve("Script_" + (idx++) + ".kt");
            Files.writeString(out, KotlinSourceTemplates.source(modulePackage, src, fs), StandardCharsets.UTF_8);
        }

        // Compile
        Files.deleteIfExists(outJar);

        String classpath = buildCompilerClasspath();

        if (manifest.debug()) {
            log.info("[" + manifest.id() + "] compiling (cache miss): " + cacheKey);
        }

        List<Path> kotlinSources = Files.list(genSrcDir)
                .filter(p -> p.toString().endsWith(".kt"))
                .sorted()
                .collect(Collectors.toList());

        KotlinCompilerInvoker.CompileResult result = kotlin.compileJvmJar(
                kotlinSources,
                classpath,
                outJar,
                manifest.id(),
                "21"
        );

        if (!result.success()) {
            log.error("[" + manifest.id() + "] Kotlin compile failed:");
            for (String line : result.messages()) {
                log.error(line);
            }
            throw new KotlinCompilationException(
                    "Kotlin compilation failed for module " + manifest.id(),
                    result.messages()
            );
        }

        // Write cache
        Files.copy(outJar, cachedJar, StandardCopyOption.REPLACE_EXISTING);

        return outJar;
    }

        private String buildCompilerClasspath() throws Exception {
        // Allow only: Focuscript API + Kotlin stdlib
        List<Path> cp = new ArrayList<>();
        cp.add(apiJarPath);

        // Kotlin stdlib jar location from runtime classpath
        Path stdlib = JarUtil.locationOf(kotlin.Unit.class);
        if (stdlib != null) cp.add(stdlib);

        // De-duplicate
        LinkedHashSet<Path> unique = new LinkedHashSet<>(cp);
        return unique.stream()
                .map(Path::toString)
                .collect(Collectors.joining(java.io.File.pathSeparator));
    }

private static void tryAdd(List<Path> list, Path p) {
        if (p == null) return;
        if (!list.contains(p)) list.add(p);
    }

    private static List<Path> collectFsSources(Path workspaceRoot) throws IOException {
        Path srcDir = workspaceRoot.resolve("src");
        if (!Files.isDirectory(srcDir)) return List.of();

        try (var walk = Files.walk(srcDir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".fs"))
                    .collect(Collectors.toList());
        }
    }

    private String computeCacheKey(String scriptYmlText, List<Path> sources, ScriptManifest manifest) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        digest.update(("focuscript-plugin-version:" + plugin.getDescription().getVersion()).getBytes(StandardCharsets.UTF_8));
        digest.update(("focuscript-api-version:" + FsApi.API_VERSION).getBytes(StandardCharsets.UTF_8));
        digest.update(("module-api-field:" + manifest.api()).getBytes(StandardCharsets.UTF_8));
        digest.update(("kotlin-compiler-version:" + KOTLIN_COMPILER_VERSION).getBytes(StandardCharsets.UTF_8));
        digest.update(("jvmTarget:21").getBytes(StandardCharsets.UTF_8));

        digest.update("script.yml".getBytes(StandardCharsets.UTF_8));
        digest.update(scriptYmlText.getBytes(StandardCharsets.UTF_8));

        for (Path p : sources) {
            digest.update(p.toString().getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(p));
        }

        byte[] hash = digest.digest();
        return toHex(hash);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit((b & 0xF), 16));
        }
        return sb.toString();
    }

    private static void validateNoPackageOrImport(String text, Path file) throws IOException {
        // Keep MVP simple: no package/import in .fs (prelude is automatic).
        // You can expand this later if you implement a proper transformer.
        var m = java.util.regex.Pattern.compile("(?m)^\s*(package|import)\s+").matcher(text);
        if (m.find()) {
            throw new IOException("Focuscript .fs must not declare package/import (prelude is automatic). Offending file: " + file);
        }
    }

    private static boolean startsWithModuleCall(String text) {
        // Allow whitespace + //comments + /*comments*/ before `module`
        int i = 0;
        int n = text.length();

        while (i < n) {
            // whitespace
            while (i < n && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= n) return false;

            // line comment
            if (i + 1 < n && text.charAt(i) == '/' && text.charAt(i + 1) == '/') {
                i += 2;
                while (i < n && text.charAt(i) != '\n') i++;
                continue;
            }

            // block comment
            if (i + 1 < n && text.charAt(i) == '/' && text.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                if (i + 1 < n) i += 2;
                continue;
            }

            // actual token
            break;
        }

        return text.startsWith("module", i);
    }

    private static String sanitizeIdAsPackagePart(String id) {
        // allow a-zA-Z0-9_ only
        String s = id.replaceAll("[^a-zA-Z0-9_]", "_");
        if (s.isEmpty()) s = "module";
        if (Character.isDigit(s.charAt(0))) s = "m_" + s;
        return s;
    }

    private static String sanitizeIdAsPath(String id) {
        if (id == null || id.isBlank()) return "module";
        String s = id.replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (s.isBlank()) s = "module";
        return s;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
