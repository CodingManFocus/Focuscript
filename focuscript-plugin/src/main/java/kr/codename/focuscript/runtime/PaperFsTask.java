package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.scheduler.FsTask;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class PaperFsTask implements FsTask {

    private final BukkitTask handle;

    public PaperFsTask(BukkitTask handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public BukkitTask getHandle() {
        return handle;
    }

    @Override
    public void cancel() {
        handle.cancel();
    }

    @Override
    public boolean isCancelled() {
        return handle.isCancelled();
    }
}
