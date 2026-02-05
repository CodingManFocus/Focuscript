package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsInventory;
import kr.codename.focuscript.api.FsItemStack;
import kr.codename.focuscript.api.FsLocation;
import kr.codename.focuscript.api.FsPlayer;
import kr.codename.focuscript.api.FsText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class PaperFsPlayer implements FsPlayer {

    private final Player handle;

    public PaperFsPlayer(Player handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public Player getHandle() {
        return handle;
    }

    @Override
    public UUID getUniqueId() {
        return handle.getUniqueId();
    }

    @Override
    public String getName() {
        return handle.getName();
    }

    @Override
    public FsLocation getLocation() {
        return PaperFsLocation.fromBukkit(handle.getLocation());
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) return false;
        return handle.hasPermission(permission);
    }

    @Override
    public boolean teleport(FsLocation location) {
        if (location == null) return false;
        World world = Bukkit.getWorld(location.getWorldName());
        if (world == null) return false;
        Location target = new Location(
                world,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
        return handle.teleport(target);
    }

    @Override
    public double getHealth() {
        return handle.getHealth();
    }

    @Override
    public double getMaxHealth() {
        return handle.getMaxHealth();
    }

    @Override
    public int getFoodLevel() {
        return handle.getFoodLevel();
    }

    @Override
    public FsInventory getInventory() {
        return new PaperFsInventory(handle.getInventory());
    }

    @Override
    public FsItemStack getItemInMainHand() {
        return PaperItemStackMapper.toFs(handle.getInventory().getItemInMainHand());
    }

    @Override
    public void setItemInMainHand(FsItemStack item) {
        handle.getInventory().setItemInMainHand(PaperItemStackMapper.toBukkit(item));
    }

    @Override
    public FsItemStack getItemInOffHand() {
        return PaperItemStackMapper.toFs(handle.getInventory().getItemInOffHand());
    }

    @Override
    public void setItemInOffHand(FsItemStack item) {
        handle.getInventory().setItemInOffHand(PaperItemStackMapper.toBukkit(item));
    }

    @Override
    public void sendText(FsText text) {
        if (text == null) return;
        handle.sendMessage(PaperTextRenderer.toComponent(text));
    }

    @Override
    public void sendActionBar(FsText text) {
        if (text == null) return;
        handle.sendActionBar(PaperTextRenderer.toComponent(text));
    }
}
