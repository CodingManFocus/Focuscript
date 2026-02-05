package kr.codename.focuscript.api.events;

import kr.codename.focuscript.api.FsPlayer;

import java.util.Objects;

public final class FsPlayerDeathEvent {
    private final FsPlayer player;
    private final String message;

    public FsPlayerDeathEvent(FsPlayer player, String message) {
        this.player = Objects.requireNonNull(player, "player");
        this.message = Objects.requireNonNull(message, "message");
    }

    public FsPlayer getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }
}
