package kr.codename.focuscript.api;

public interface FsCommand {
    String getName();

    String getPermission();

    void unregister();
}
