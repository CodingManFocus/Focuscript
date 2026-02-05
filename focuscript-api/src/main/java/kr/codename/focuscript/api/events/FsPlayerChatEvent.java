package kr.codename.focuscript.api.events;

import kr.codename.focuscript.api.FsPlayer;

import java.util.Objects;

public final class FsPlayerChatEvent {
    private final FsPlayer player;
    private final String message;
    private final boolean async;

    public FsPlayerChatEvent(FsPlayer player, String message, boolean async) {
        this.player = Objects.requireNonNull(player, "player");
        this.message = Objects.requireNonNull(message, "message");
        this.async = async;
    }

    public FsPlayer getPlayer() {
        return player;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAsync() {
        return async;
    }
}
