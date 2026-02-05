package kr.codename.focuscript.api;

import kr.codename.focuscript.api.scheduler.FsTask;

import java.time.Duration;

public interface FsScheduler {
    FsTask after(Duration delay, Runnable task);
    FsTask every(Duration period, Runnable task);

    /**
     * Cancel all tasks created via this scheduler (module-scoped).
     */
    void cancelAll();
}
