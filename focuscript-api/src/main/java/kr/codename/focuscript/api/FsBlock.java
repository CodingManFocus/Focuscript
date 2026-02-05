package kr.codename.focuscript.api;

public interface FsBlock {
    FsLocation getLocation();

    String getType();

    void setType(String type);
}
