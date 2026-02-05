package kr.codename.focuscript.api.scheduler;

public interface FsTask {
    void cancel();
    boolean isCancelled();
}
