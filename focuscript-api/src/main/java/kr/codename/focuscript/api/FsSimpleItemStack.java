package kr.codename.focuscript.api;

public final class FsSimpleItemStack implements FsItemStack {
    private String type;
    private int amount;
    private FsItemMeta meta;

    public FsSimpleItemStack(String type, int amount, FsItemMeta meta) {
        this.type = type;
        this.amount = amount;
        this.meta = meta;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public void setType(String type) {
        this.type = type;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public FsItemMeta getItemMeta() {
        return meta;
    }

    @Override
    public boolean setItemMeta(FsItemMeta meta) {
        this.meta = meta;
        return true;
    }
}
