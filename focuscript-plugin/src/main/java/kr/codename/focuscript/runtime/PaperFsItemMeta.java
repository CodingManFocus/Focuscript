package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsItemMeta;
import kr.codename.focuscript.api.FsText;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PaperFsItemMeta implements FsItemMeta {
    private final ItemMeta handle;

    public PaperFsItemMeta(ItemMeta handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    ItemMeta getHandle() {
        return handle;
    }

    @Override
    public FsText getDisplayName() {
        Component name = handle.displayName();
        if (name == null) return null;
        return PaperTextRenderer.fromComponent(name);
    }

    @Override
    public void setDisplayName(FsText text) {
        if (text == null) {
            handle.displayName(null);
            return;
        }
        handle.displayName(PaperTextRenderer.toComponent(text));
    }

    @Override
    public List<FsText> getLore() {
        List<Component> lore = handle.lore();
        if (lore == null || lore.isEmpty()) return List.of();
        List<FsText> out = new ArrayList<>(lore.size());
        for (Component line : lore) {
            out.add(PaperTextRenderer.fromComponent(line));
        }
        return out;
    }

    @Override
    public void setLore(List<FsText> lore) {
        if (lore == null) {
            handle.lore(null);
            return;
        }
        List<Component> comps = new ArrayList<>(lore.size());
        for (FsText line : lore) {
            if (line == null) {
                comps.add(Component.empty());
            } else {
                comps.add(PaperTextRenderer.toComponent(line));
            }
        }
        handle.lore(comps);
    }

    @Override
    public boolean hasCustomModelData() {
        return handle.hasCustomModelData();
    }

    @Override
    public Integer getCustomModelData() {
        if (!handle.hasCustomModelData()) return null;
        return handle.getCustomModelData();
    }

    @Override
    public void setCustomModelData(Integer data) {
        handle.setCustomModelData(data);
    }
}
