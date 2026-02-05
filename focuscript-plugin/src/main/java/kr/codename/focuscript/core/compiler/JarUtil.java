package kr.codename.focuscript.core.compiler;

import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.security.CodeSource;

/**
 * Helper for locating jar/directory paths of classes on the runtime classpath.
 */
public final class JarUtil {
    private JarUtil() {}

    public static Path locationOf(Class<?> cls) {
        try {
            CodeSource source = cls.getProtectionDomain().getCodeSource();
            if (source == null) return null;

            URL url = source.getLocation();
            if (url == null) return null;

            // Some classloaders provide a "jar:" URL (e.g. jar:file:/...!/).
            // Path.of(URI) with scheme "jar" requires an attached ZipFileSystem;
            // if it's not mounted you'll get FileSystemNotFoundException.
            // We only need the *actual jar file path* for compiler classpaths, so
            // convert jar: URLs to the underlying file: URL.
            if ("jar".equalsIgnoreCase(url.getProtocol())) {
                // First try the standard JarURLConnection route.
                try {
                    URLConnection conn = url.openConnection();
                    if (conn instanceof JarURLConnection juc) {
                        URL jarFileUrl = juc.getJarFileURL();
                        return Path.of(jarFileUrl.toURI());
                    }
                } catch (Exception ignored) {
                    // fall back to manual parsing
                }

                // Fallback: strip the "jar:" prefix and everything after "!/".
                String spec = url.toString();
                if (spec.startsWith("jar:")) spec = spec.substring(4);
                int sep = spec.indexOf("!/");
                if (sep != -1) spec = spec.substring(0, sep);
                return Path.of(new URL(spec).toURI());
            }

            // Common case: "file:" URL pointing to a jar on disk.
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                return Path.of(url.toURI());
            }

            // Unknown / non-pathable URL protocol.
            return null;
        } catch (URISyntaxException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
