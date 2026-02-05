package kr.codename.focuscript.api;

public interface FsCommandSender {
    String getName();

    boolean isPlayer();

    FsPlayer getPlayer();

    boolean hasPermission(String permission);

    void sendText(FsText text);
}
