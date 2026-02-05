package kr.codename.focuscript.runtime;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.api.FsScheduler;
import kr.codename.focuscript.api.scheduler.FsTask;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PaperFsScheduler implements FsScheduler {

    private final FocuscriptPlugin plugin;
    private final String moduleId;
    private final PaperFsLogger log;

    private final CopyOnWriteArrayList<PaperFsTask> tasks = new CopyOnWriteArrayList<>();

    public PaperFsScheduler(FocuscriptPlugin plugin, String moduleId, PaperFsLogger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public FsTask after(Duration delay, Runnable task) {
        Objects.requireNonNull(task, "task");
        long ticks = durationToTicks(delay);

        BukkitTask handle = Bukkit.getScheduler().runTaskLater(plugin, safe(task), ticks);
        PaperFsTask fsTask = new PaperFsTask(handle);
        tasks.add(fsTask);
        return fsTask;
    }

    @Override
    public FsTask every(Duration period, Runnable task) {
        Objects.requireNonNull(task, "task");
        long ticks = durationToTicks(period);
        if (ticks <= 0) ticks = 1;

        BukkitTask handle = Bukkit.getScheduler().runTaskTimer(plugin, safe(task), ticks, ticks);
        PaperFsTask fsTask = new PaperFsTask(handle);
        tasks.add(fsTask);
        return fsTask;
    }

    @Override
    public void cancelAll() {
        List<PaperFsTask> snapshot = List.copyOf(tasks);
        int cancelFailures = 0;

        for (PaperFsTask task : snapshot) {
            try {
                task.cancel();
            } catch (Throwable t) {
                cancelFailures++;
                log.warn("Task cancel failed: " + t.getMessage());
            }
        }

        int active = 0;
        for (PaperFsTask task : snapshot) {
            int taskId = task.getHandle().getTaskId();
            if (Bukkit.getScheduler().isQueued(taskId) || Bukkit.getScheduler().isCurrentlyRunning(taskId)) {
                active++;
            }
        }

        tasks.clear();

        if (cancelFailures > 0) {
            log.warn("Possible scheduler leak: " + cancelFailures + " task(s) failed to cancel");
        }
        if (active > 0) {
            log.warn("Possible scheduler leak: " + active + " task(s) still queued or running after cancelAll()");
        }
    }

    private Runnable safe(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                log.error("Task error", t);
            }
        };
    }

    private static long durationToTicks(Duration d) {
        if (d == null) return 0;
        long ms = d.toMillis();
        if (ms <= 0) return 0;
        return (ms + 49L) / 50L; // ceil(ms/50)
    }
}
