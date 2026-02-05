package kr.codename.focuscript.api.events;

import kr.codename.focuscript.api.FsPlayer;

import java.util.Objects;

public final class FsPlayerCommandEvent {
    private final FsPlayer player;
    private final String commandLine;

    public FsPlayerCommandEvent(FsPlayer player, String commandLine) {
        this.player = Objects.requireNonNull(player, "player");
        this.commandLine = Objects.requireNonNull(commandLine, "commandLine");
    }

    public FsPlayer getPlayer() {
        return player;
    }

    public String getCommandLine() {
        return commandLine;
    }
}
