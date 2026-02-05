package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsInventory;
import kr.codename.focuscript.api.FsItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PaperFsInventory implements FsInventory {
    private final Inventory handle;

    public PaperFsInventory(Inventory handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    Inventory getHandle() {
        return handle;
    }

    @Override
    public int getSize() {
        return handle.getSize();
    }

    @Override
    public FsItemStack getItem(int slot) {
        return PaperItemStackMapper.toFs(handle.getItem(slot));
    }

    @Override
    public void setItem(int slot, FsItemStack item) {
        ItemStack stack = PaperItemStackMapper.toBukkit(item);
        handle.setItem(slot, stack);
    }

    @Override
    public void clear() {
        handle.clear();
    }

    @Override
    public Map<Integer, FsItemStack> addItem(FsItemStack... items) {
        if (items == null || items.length == 0) return Map.of();
        ItemStack[] stacks = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            stacks[i] = PaperItemStackMapper.toBukkit(items[i]);
        }
        Map<Integer, ItemStack> leftovers = handle.addItem(stacks);
        if (leftovers.isEmpty()) return Map.of();

        Map<Integer, FsItemStack> mapped = new HashMap<>();
        for (var entry : leftovers.entrySet()) {
            mapped.put(entry.getKey(), PaperItemStackMapper.toFs(entry.getValue()));
        }
        return mapped;
    }
}
