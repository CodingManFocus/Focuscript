package kr.codename.focuscript.api;

public interface FsLogger {
    void info(String message);
    void warn(String message);
    void debug(String message);

    void error(String message);
    void error(String message, Throwable throwable);
}
