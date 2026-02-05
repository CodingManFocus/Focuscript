package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsBlock;
import kr.codename.focuscript.api.FsLocation;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.Objects;

public final class PaperFsBlock implements FsBlock {
    private final Block handle;

    public PaperFsBlock(Block handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    Block getHandle() {
        return handle;
    }

    @Override
    public FsLocation getLocation() {
        return PaperFsLocation.fromBukkit(handle.getLocation());
    }

    @Override
    public String getType() {
        return handle.getType().getKey().toString();
    }

    @Override
    public void setType(String type) {
        Material material = PaperItemStackMapper.matchMaterial(type);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + type);
        }
        handle.setType(material);
    }
}
