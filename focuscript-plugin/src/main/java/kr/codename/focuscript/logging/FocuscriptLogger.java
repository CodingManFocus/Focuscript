package kr.codename.focuscript.logging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Objects;

public final class FocuscriptLogger {
    private static final String DEFAULT_PREFIX = "<color:#5F5FFF>Focuscript</color>) ";

    private final ComponentLogger logger;
    private final String prefix;
    private final boolean debugEnabled;

    private static final MiniMessage MiniMsg = MiniMessage.miniMessage();

    public FocuscriptLogger(ComponentLogger logger) {
        this(logger, DEFAULT_PREFIX, false);
    }

    public FocuscriptLogger(ComponentLogger logger, String prefix, boolean debugEnabled) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.debugEnabled = debugEnabled;
    }

    public ComponentLogger raw() {
        return logger;
    }

    public void info(String message) {
        logger.info(render(message));
    }

    public void warn(String message) {
        logger.warn(render(message));
    }

    public void error(String message) {
        logger.error(render(message));
    }

    public void error(String message, Throwable throwable) {
        logger.error(render(message), throwable);
    }

    public void debug(String message) {
        if (!debugEnabled) return;
        Component focuscript = MiniMsg.deserialize("<color:#5F5FFF>Focuscript</color>)");
        logger.info(focuscript.append(render(message)));
    }

    private Component render(String message) {
        if (message == null || message.isBlank()) {
            return MiniMsg.deserialize(prefix.trim());
        }
        return MiniMsg.deserialize(prefix + message);
    }
}
