package kr.codename.focuscript.api.events;

import kr.codename.focuscript.api.FsPlayer;

import java.util.Objects;

public final class FsPlayerJoinEvent {
    private final FsPlayer player;

    public FsPlayerJoinEvent(FsPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public FsPlayer getPlayer() {
        return player;
    }
}
