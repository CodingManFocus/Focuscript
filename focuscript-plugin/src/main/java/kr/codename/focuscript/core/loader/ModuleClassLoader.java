package kr.codename.focuscript.core.loader;

import kr.codename.focuscript.FocuscriptPlugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;

/**
 * Module-dedicated classloader with package blocking.
 *
 * Goals:
 * 1) Modules can use JDK + Kotlin stdlib + Focuscript API.
 * 2) Modules should NOT directly load Paper/Adventure classes.
 * 3) Modules should NOT load Focuscript internal (non-API) classes.
 *
 * NOTE:
 * This is a best-effort sandbox; Java does not provide a perfect sandbox.
 */
public final class ModuleClassLoader extends URLClassLoader {

    private static final String FS_API_PREFIX = "kr.codename.focuscript.api.";
    private static final String FS_MODULES_PREFIX = "kr.codename.focuscript.modules.";
    private static final String FS_INTERNAL_PREFIX = "kr.codename.focuscript.";

    private static final List<String> BLOCKED_PREFIXES = List.of(
            "org.bukkit.",
            "org.spigotmc.",
            "io.papermc.",
            "com.destroystokyo.paper.",
            "net.kyori.adventure."
    );

    private final FocuscriptPlugin plugin;
    private final String moduleId;

    public ModuleClassLoader(FocuscriptPlugin plugin, String moduleId, URL moduleJarUrl, ClassLoader parent) {
        super(new URL[]{Objects.requireNonNull(moduleJarUrl, "moduleJarUrl")}, parent);
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Block Paper/Adventure packages
        for (String blocked : BLOCKED_PREFIXES) {
            if (name.startsWith(blocked)) {
                throw new ClassNotFoundException("Blocked class access from module '" + moduleId + "': " + name);
            }
        }

        // Block Focuscript internals (allow only API + generated module packages)
        if (name.startsWith(FS_INTERNAL_PREFIX)
                && !name.startsWith(FS_API_PREFIX)
                && !name.startsWith(FS_MODULES_PREFIX)) {
            throw new ClassNotFoundException("Blocked Focuscript internal class from module '" + moduleId + "': " + name);
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            boolean isModuleClass = name.startsWith(FS_MODULES_PREFIX);

            if (isModuleClass) {
                // Child-first for module-generated package
                try {
                    Class<?> found = findClass(name);
                    if (resolve) resolveClass(found);
                    return found;
                } catch (ClassNotFoundException ignored) {
                    // fallback to parent
                }
            }

            // Parent-first for everything else (JDK/Kotlin/API)
            try {
                Class<?> parentClass = getParent().loadClass(name);
                if (resolve) resolveClass(parentClass);
                return parentClass;
            } catch (ClassNotFoundException ignored) {
                // fallback to module jar
            }

            Class<?> found = findClass(name);
            if (resolve) resolveClass(found);
            return found;
        }
    }
}
