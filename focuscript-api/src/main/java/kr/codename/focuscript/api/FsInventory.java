package kr.codename.focuscript.api;

import java.util.Map;

public interface FsInventory {
    int getSize();

    FsItemStack getItem(int slot);

    void setItem(int slot, FsItemStack item);

    void clear();

    Map<Integer, FsItemStack> addItem(FsItemStack... items);
}
