package kr.codename.focuscript.api;

@FunctionalInterface
public interface FsCommandHandler {
    void handle(FsCommandContext context);
}
