package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsBlock;
import kr.codename.focuscript.api.FsLocation;
import kr.codename.focuscript.api.FsWorld;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class PaperFsWorld implements FsWorld {
    private final World handle;

    public PaperFsWorld(World handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    World getHandle() {
        return handle;
    }

    @Override
    public String getName() {
        return handle.getName();
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUID();
    }

    @Override
    public FsBlock getBlockAt(int x, int y, int z) {
        return new PaperFsBlock(handle.getBlockAt(x, y, z));
    }

    @Override
    public FsBlock getBlockAt(FsLocation location) {
        if (location == null) return null;
        int x = (int) Math.floor(location.getX());
        int y = (int) Math.floor(location.getY());
        int z = (int) Math.floor(location.getZ());
        return new PaperFsBlock(handle.getBlockAt(x, y, z));
    }
}
