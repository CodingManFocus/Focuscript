package kr.codename.focuscript.api.events;

import kr.codename.focuscript.api.FsLocation;
import kr.codename.focuscript.api.FsPlayer;

import java.util.Objects;

public final class FsPlayerBlockBreakEvent {
    private final FsPlayer player;
    private final FsLocation location;
    private final String blockType;

    public FsPlayerBlockBreakEvent(FsPlayer player, FsLocation location, String blockType) {
        this.player = Objects.requireNonNull(player, "player");
        this.location = Objects.requireNonNull(location, "location");
        this.blockType = Objects.requireNonNull(blockType, "blockType");
    }

    public FsPlayer getPlayer() {
        return player;
    }

    public FsLocation getLocation() {
        return location;
    }

    public String getBlockType() {
        return blockType;
    }
}
