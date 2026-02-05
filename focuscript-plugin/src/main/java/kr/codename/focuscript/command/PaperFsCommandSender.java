package kr.codename.focuscript.command;

import kr.codename.focuscript.api.FsCommandSender;
import kr.codename.focuscript.api.FsPlayer;
import kr.codename.focuscript.api.FsText;
import kr.codename.focuscript.runtime.PaperFsPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class PaperFsCommandSender implements FsCommandSender {
    private final CommandSender sender;

    public PaperFsCommandSender(CommandSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    @Override
    public FsPlayer getPlayer() {
        if (sender instanceof Player player) {
            return new PaperFsPlayer(player);
        }
        return null;
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) return false;
        return sender.hasPermission(permission);
    }

    @Override
    public void sendText(FsText text) {
        if (text == null) return;
        sender.sendMessage(text.plain());
    }
}
