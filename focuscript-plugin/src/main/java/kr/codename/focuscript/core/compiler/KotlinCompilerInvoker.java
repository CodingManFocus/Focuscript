package kr.codename.focuscript.core.compiler;

import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.codename.focuscript.logging.FocuscriptLogger;

/**
 * Minimal Kotlin compiler invoker (embeddable).
 */
public final class KotlinCompilerInvoker {

    private final FocuscriptLogger log;

    public KotlinCompilerInvoker(FocuscriptLogger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public record CompileResult(boolean success, List<String> messages) {}

    public CompileResult compileJvmJar(
            List<Path> sources,
            String classpath,
            Path outJar,
            String moduleName,
            String jvmTarget
    ) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(classpath, "classpath");
        Objects.requireNonNull(outJar, "outJar");

        CollectingMessageCollector collector = new CollectingMessageCollector();

        K2JVMCompilerArguments args = new K2JVMCompilerArguments();
        args.setFreeArgs(sources.stream().map(Path::toString).toList());
        args.setDestination(outJar.toString());
        args.setClasspath(classpath);

        // We provide stdlib explicitly in classpath.
        args.setNoStdlib(true);
        args.setNoReflect(true);

        args.setModuleName(sanitizeModuleName(moduleName));
        args.setJvmTarget(jvmTarget);

        // Be less noisy by default (still collect WARN/ERROR).
        args.setVerbose(false);

        ExitCode code;
        try {
            code = new K2JVMCompiler().exec(collector, Services.EMPTY, args);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            List<String> msgs = new ArrayList<>();
            msgs.add("Kotlin compiler crashed: " + t);
            msgs.add(sw.toString());
            return new CompileResult(false, msgs);
        }

        boolean ok = code == ExitCode.OK && !collector.hasErrors();
        return new CompileResult(ok, collector.messages);
    }

    private static String sanitizeModuleName(String s) {
        if (s == null || s.isBlank()) return "focuscript_module";
        return s.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    private static final class CollectingMessageCollector implements MessageCollector {
        private final List<String> messages = new ArrayList<>();
        private boolean hasErrors = false;

        @Override
        public void clear() {
            messages.clear();
            hasErrors = false;
        }

        @Override
        public boolean hasErrors() {
            return hasErrors;
        }

        @Override
        public void report(CompilerMessageSeverity severity, String message, CompilerMessageSourceLocation location) {
            if (severity == CompilerMessageSeverity.ERROR) {
                hasErrors = true;
            }
            if (severity == CompilerMessageSeverity.LOGGING) {
                // Skip very verbose logs
                return;
            }

            String loc = "";
            if (location != null && location.getPath() != null) {
                loc = location.getPath();
                if (location.getLine() > 0) {
                    loc += ":" + location.getLine() + ":" + location.getColumn();
                }
                loc += ": ";
            }

            messages.add("[" + severity + "] " + loc + message);
        }
    }
}
