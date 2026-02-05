package kr.codename.focuscript.api;

import java.util.List;

public interface FsItemMeta {
    FsText getDisplayName();

    void setDisplayName(FsText text);

    List<FsText> getLore();

    void setLore(List<FsText> lore);

    boolean hasCustomModelData();

    Integer getCustomModelData();

    void setCustomModelData(Integer data);
}
