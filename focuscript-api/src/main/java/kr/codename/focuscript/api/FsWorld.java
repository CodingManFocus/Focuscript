package kr.codename.focuscript.api;

import java.util.UUID;

public interface FsWorld {
    String getName();

    UUID getUniqueId();

    FsBlock getBlockAt(int x, int y, int z);

    FsBlock getBlockAt(FsLocation location);
}
