package kr.codename.focuscript.runtime;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.api.FsPlayer;
import kr.codename.focuscript.api.FsServer;
import kr.codename.focuscript.api.FsText;
import kr.codename.focuscript.api.FsWorld;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class PaperFsServer implements FsServer {

    private final FocuscriptPlugin plugin;
    private final PaperFsLogger log;

    public PaperFsServer(FocuscriptPlugin plugin, PaperFsLogger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Collection<FsPlayer> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
                .map(PaperFsPlayer::new)
                .collect(Collectors.toList());
    }

    @Override
    public FsPlayer getPlayer(UUID uniqueId) {
        if (uniqueId == null) return null;
        Player p = Bukkit.getPlayer(uniqueId);
        if (p == null) return null;
        return new PaperFsPlayer(p);
    }

    @Override
    public FsPlayer getPlayer(String name) {
        if (name == null || name.isBlank()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) return null;
        return new PaperFsPlayer(p);
    }

    @Override
    public int getOnlinePlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public Collection<FsWorld> getWorlds() {
        return Bukkit.getWorlds().stream()
                .map(PaperFsWorld::new)
                .collect(Collectors.toList());
    }

    @Override
    public FsWorld getWorld(String name) {
        if (name == null || name.isBlank()) return null;
        var world = Bukkit.getWorld(name);
        if (world == null) return null;
        return new PaperFsWorld(world);
    }

    @Override
    public void broadcast(FsText text) {
        if (text == null) return;
        var comp = PaperTextRenderer.toComponent(text);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(comp);
        }
        // Also log to console (optional)
        log.info("[broadcast] " + text.plain());
    }

    @Override
    public boolean dispatchCommand(String command) {
        if (command == null || command.isBlank()) return false;
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
