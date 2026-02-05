package kr.codename.focuscript.api;

import java.util.UUID;

public interface FsPlayer {
    UUID getUniqueId();

    String getName();

    FsLocation getLocation();

    boolean hasPermission(String permission);

    boolean teleport(FsLocation location);

    double getHealth();

    double getMaxHealth();

    int getFoodLevel();

    FsInventory getInventory();

    FsItemStack getItemInMainHand();

    void setItemInMainHand(FsItemStack item);

    FsItemStack getItemInOffHand();

    void setItemInOffHand(FsItemStack item);

    void sendText(FsText text);

    void sendActionBar(FsText text);
}
