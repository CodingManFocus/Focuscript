package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsLogger;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Objects;

public final class PaperFsLogger implements FsLogger {

    private final ComponentLogger logger;
    private final String prefix;
    private final boolean debugEnabled;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PaperFsLogger(ComponentLogger logger, String moduleId, boolean debugEnabled) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.prefix = "[FS:" + Objects.requireNonNull(moduleId, "moduleId") + "] ";
        this.debugEnabled = debugEnabled;
    }

    @Override
    public void info(String message) {
        logger.info(miniMessage.deserialize(prefix + message));
    }

    @Override
    public void warn(String message) {
        logger.warn(miniMessage.deserialize(prefix + message));
    }

    @Override
    public void debug(String message) {
        if (!debugEnabled) return;
        logger.info(miniMessage.deserialize(prefix + "[debug] " + message));
    }

    @Override
    public void error(String message) {
        logger.error(miniMessage.deserialize(prefix + message));
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(miniMessage.deserialize(prefix + message), throwable);
    }
}
