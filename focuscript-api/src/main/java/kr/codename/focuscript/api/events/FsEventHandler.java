package kr.codename.focuscript.api.events;

@FunctionalInterface
public interface FsEventHandler<T> {
    void handle(T event);
}
