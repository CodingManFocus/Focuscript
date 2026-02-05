package kr.codename.focuscript.api;

public interface FsCommands {
    FsCommand register(String name, FsCommandHandler handler);

    FsCommand register(String name, String permission, FsCommandHandler handler);

    void unregisterAll();
}
