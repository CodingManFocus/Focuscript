package kr.codename.focuscript.api;

import java.util.Collection;
import java.util.UUID;

public interface FsServer {
    Collection<FsPlayer> getOnlinePlayers();

    FsPlayer getPlayer(UUID uniqueId);

    FsPlayer getPlayer(String name);

    int getOnlinePlayerCount();

    Collection<FsWorld> getWorlds();

    FsWorld getWorld(String name);

    void broadcast(FsText text);

    boolean dispatchCommand(String command);
}
