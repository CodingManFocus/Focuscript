package kr.codename.focuscript.api;

import java.util.ArrayList;
import java.util.List;

public final class FsSimpleItemMeta implements FsItemMeta {
    private FsText displayName;
    private List<FsText> lore = List.of();
    private boolean hasCustomModelData;
    private Integer customModelData;

    @Override
    public FsText getDisplayName() {
        return displayName;
    }

    @Override
    public void setDisplayName(FsText text) {
        this.displayName = text;
    }

    @Override
    public List<FsText> getLore() {
        return List.copyOf(lore);
    }

    @Override
    public void setLore(List<FsText> lore) {
        if (lore == null) {
            this.lore = List.of();
            return;
        }
        this.lore = new ArrayList<>(lore);
    }

    @Override
    public boolean hasCustomModelData() {
        return hasCustomModelData;
    }

    @Override
    public Integer getCustomModelData() {
        return customModelData;
    }

    @Override
    public void setCustomModelData(Integer data) {
        if (data == null) {
            this.hasCustomModelData = false;
            this.customModelData = null;
            return;
        }
        this.hasCustomModelData = true;
        this.customModelData = data;
    }
}
