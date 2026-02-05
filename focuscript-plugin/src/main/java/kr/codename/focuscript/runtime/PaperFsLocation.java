package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsLocation;
import org.bukkit.Location;

import java.util.Objects;

public record PaperFsLocation(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) implements FsLocation {

    public static PaperFsLocation fromBukkit(Location loc) {
        Objects.requireNonNull(loc, "loc");
        var world = loc.getWorld();
        String worldName = world != null ? world.getName() : "unknown";
        return new PaperFsLocation(worldName, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public float getYaw() {
        return yaw;
    }

    @Override
    public float getPitch() {
        return pitch;
    }
}
