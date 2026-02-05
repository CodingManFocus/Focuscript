package kr.codename.focuscript.core.bridge;

import kr.codename.focuscript.FocuscriptPlugin;
import kr.codename.focuscript.api.events.FsEventHandler;
import kr.codename.focuscript.logging.FocuscriptLogger;
import kr.codename.focuscript.api.events.FsPlayerBlockBreakEvent;
import kr.codename.focuscript.api.events.FsPlayerBlockPlaceEvent;
import kr.codename.focuscript.api.events.FsPlayerChatEvent;
import kr.codename.focuscript.api.events.FsPlayerCommandEvent;
import kr.codename.focuscript.api.events.FsPlayerDamageEvent;
import kr.codename.focuscript.api.events.FsPlayerDeathEvent;
import kr.codename.focuscript.api.events.FsPlayerJoinEvent;
import kr.codename.focuscript.api.events.FsPlayerQuitEvent;
import kr.codename.focuscript.api.events.FsSubscription;
import kr.codename.focuscript.runtime.PaperFsLocation;
import kr.codename.focuscript.runtime.PaperFsPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Paper -> Focuscript event bridge.
 *
 * Registered once at plugin startup. Modules register handlers through PaperFsEvents.
 */
public final class PaperEventBridge implements Listener {

    private record Reg<T>(String moduleId, FsEventHandler<T> handler) {}

    private final FocuscriptPlugin plugin;
    private final FocuscriptLogger log;

    private final CopyOnWriteArrayList<Reg<FsPlayerJoinEvent>> joinHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerQuitEvent>> quitHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerChatEvent>> chatHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerCommandEvent>> commandHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerBlockBreakEvent>> blockBreakHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerBlockPlaceEvent>> blockPlaceHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerDeathEvent>> deathHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Reg<FsPlayerDamageEvent>> damageHandlers = new CopyOnWriteArrayList<>();

    public PaperEventBridge(FocuscriptPlugin plugin, FocuscriptLogger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.log = Objects.requireNonNull(log, "log");
    }

    public FsSubscription registerJoin(String moduleId, FsEventHandler<FsPlayerJoinEvent> handler) {
        Reg<FsPlayerJoinEvent> reg = new Reg<>(moduleId, handler);
        joinHandlers.add(reg);
        return () -> joinHandlers.remove(reg);
    }

    public FsSubscription registerQuit(String moduleId, FsEventHandler<FsPlayerQuitEvent> handler) {
        Reg<FsPlayerQuitEvent> reg = new Reg<>(moduleId, handler);
        quitHandlers.add(reg);
        return () -> quitHandlers.remove(reg);
    }

    public FsSubscription registerChat(String moduleId, FsEventHandler<FsPlayerChatEvent> handler) {
        Reg<FsPlayerChatEvent> reg = new Reg<>(moduleId, handler);
        chatHandlers.add(reg);
        return () -> chatHandlers.remove(reg);
    }

    public FsSubscription registerCommand(String moduleId, FsEventHandler<FsPlayerCommandEvent> handler) {
        Reg<FsPlayerCommandEvent> reg = new Reg<>(moduleId, handler);
        commandHandlers.add(reg);
        return () -> commandHandlers.remove(reg);
    }

    public FsSubscription registerBlockBreak(String moduleId, FsEventHandler<FsPlayerBlockBreakEvent> handler) {
        Reg<FsPlayerBlockBreakEvent> reg = new Reg<>(moduleId, handler);
        blockBreakHandlers.add(reg);
        return () -> blockBreakHandlers.remove(reg);
    }

    public FsSubscription registerBlockPlace(String moduleId, FsEventHandler<FsPlayerBlockPlaceEvent> handler) {
        Reg<FsPlayerBlockPlaceEvent> reg = new Reg<>(moduleId, handler);
        blockPlaceHandlers.add(reg);
        return () -> blockPlaceHandlers.remove(reg);
    }

    public FsSubscription registerDeath(String moduleId, FsEventHandler<FsPlayerDeathEvent> handler) {
        Reg<FsPlayerDeathEvent> reg = new Reg<>(moduleId, handler);
        deathHandlers.add(reg);
        return () -> deathHandlers.remove(reg);
    }

    public FsSubscription registerDamage(String moduleId, FsEventHandler<FsPlayerDamageEvent> handler) {
        Reg<FsPlayerDamageEvent> reg = new Reg<>(moduleId, handler);
        damageHandlers.add(reg);
        return () -> damageHandlers.remove(reg);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = new PaperFsPlayer(event.getPlayer());
        var fsEvent = new FsPlayerJoinEvent(player);
        dispatch("onJoin", joinHandlers, fsEvent);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = new PaperFsPlayer(event.getPlayer());
        var fsEvent = new FsPlayerQuitEvent(player);
        dispatch("onQuit", quitHandlers, fsEvent);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        var player = new PaperFsPlayer(event.getPlayer());
        var fsEvent = new FsPlayerChatEvent(player, event.getMessage(), event.isAsynchronous());
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(plugin, () -> dispatch("onChat", chatHandlers, fsEvent));
        } else {
            dispatch("onChat", chatHandlers, fsEvent);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var player = new PaperFsPlayer(event.getPlayer());
        var fsEvent = new FsPlayerCommandEvent(player, event.getMessage());
        dispatch("onCommand", commandHandlers, fsEvent);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        var player = new PaperFsPlayer(event.getPlayer());
        var block = event.getBlock();
        var fsEvent = new FsPlayerBlockBreakEvent(
                player,
                PaperFsLocation.fromBukkit(block.getLocation()),
                block.getType().getKey().toString()
        );
        dispatch("onBlockBreak", blockBreakHandlers, fsEvent);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        var player = new PaperFsPlayer(event.getPlayer());
        var block = event.getBlockPlaced();
        var fsEvent = new FsPlayerBlockPlaceEvent(
                player,
                PaperFsLocation.fromBukkit(block.getLocation()),
                block.getType().getKey().toString()
        );
        dispatch("onBlockPlace", blockPlaceHandlers, fsEvent);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        var player = new PaperFsPlayer(event.getEntity());
        String message = event.getDeathMessage();
        if (message == null) message = "";
        var fsEvent = new FsPlayerDeathEvent(player, message);
        dispatch("onDeath", deathHandlers, fsEvent);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var fsPlayer = new PaperFsPlayer(player);
        String cause = event.getCause().name();
        String damagerType = "environment";
        FsPlayerDamageEvent fsEvent;

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            damagerType = damager.getType().getKey().toString();
            if (damager instanceof Player damagerPlayer) {
                fsEvent = new FsPlayerDamageEvent(
                        fsPlayer,
                        event.getFinalDamage(),
                        cause,
                        damagerType,
                        new PaperFsPlayer(damagerPlayer)
                );
            } else {
                fsEvent = new FsPlayerDamageEvent(
                        fsPlayer,
                        event.getFinalDamage(),
                        cause,
                        damagerType,
                        null
                );
            }
        } else {
            fsEvent = new FsPlayerDamageEvent(
                    fsPlayer,
                    event.getFinalDamage(),
                    cause,
                    damagerType,
                    null
            );
        }

        dispatch("onDamage", damageHandlers, fsEvent);
    }

    private <T> void dispatch(String hook, CopyOnWriteArrayList<Reg<T>> handlers, T fsEvent) {
        for (Reg<T> reg : handlers) {
            try {
                reg.handler().handle(fsEvent);
            } catch (Throwable t) {
                log.error("Module " + reg.moduleId() + " threw in " + hook + ": " + t.getMessage(), t);
            }
        }
    }
}
