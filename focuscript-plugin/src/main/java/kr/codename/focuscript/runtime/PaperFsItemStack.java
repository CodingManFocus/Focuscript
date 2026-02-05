package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsItemMeta;
import kr.codename.focuscript.api.FsItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public final class PaperFsItemStack implements FsItemStack {
    private final ItemStack handle;

    public PaperFsItemStack(ItemStack handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    ItemStack getHandle() {
        return handle;
    }

    @Override
    public String getType() {
        return handle.getType().getKey().toString();
    }

    @Override
    public void setType(String type) {
        Material material = PaperItemStackMapper.matchMaterial(type);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + type);
        }
        handle.setType(material);
    }

    @Override
    public int getAmount() {
        return handle.getAmount();
    }

    @Override
    public void setAmount(int amount) {
        handle.setAmount(amount);
    }

    @Override
    public FsItemMeta getItemMeta() {
        ItemMeta meta = handle.getItemMeta();
        if (meta == null) return null;
        return new PaperFsItemMeta(meta);
    }

    @Override
    public boolean setItemMeta(FsItemMeta meta) {
        if (meta == null) {
            return handle.setItemMeta(null);
        }
        ItemMeta bukkitMeta = PaperItemStackMapper.toBukkitMeta(handle, meta);
        return handle.setItemMeta(bukkitMeta);
    }
}
