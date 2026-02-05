package kr.codename.focuscript.api;

public interface FsItemStack {
    String getType();

    void setType(String type);

    int getAmount();

    void setAmount(int amount);

    FsItemMeta getItemMeta();

    boolean setItemMeta(FsItemMeta meta);

    static FsItemStack of(String type, int amount) {
        return new FsSimpleItemStack(type, amount, null);
    }

    static FsItemStack of(String type) {
        return new FsSimpleItemStack(type, 1, null);
    }
}
