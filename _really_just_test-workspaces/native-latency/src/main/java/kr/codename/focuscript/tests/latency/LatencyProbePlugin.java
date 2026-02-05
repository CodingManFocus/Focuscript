package kr.codename.focuscript.tests.latency;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.LongAdder;

public final class LatencyProbePlugin extends JavaPlugin implements Listener {
    private static final int LOG_INTERVAL_SECONDS = 5;

    private final LongAdder totalEvents = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();

    private final LongAdder joinCount = new LongAdder();
    private final LongAdder quitCount = new LongAdder();
    private final LongAdder chatCount = new LongAdder();
    private final LongAdder commandCount = new LongAdder();
    private final LongAdder blockBreakCount = new LongAdder();
    private final LongAdder blockPlaceCount = new LongAdder();
    private final LongAdder deathCount = new LongAdder();
    private final LongAdder damageCount = new LongAdder();

    private volatile String lastJoin = "";
    private volatile String lastQuit = "";
    private volatile String lastChat = "";
    private volatile String lastCommand = "";
    private volatile String lastBlockBreak = "";
    private volatile String lastBlockPlace = "";
    private volatile String lastDeath = "";
    private volatile String lastDamage = "";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        long ticks = LOG_INTERVAL_SECONDS * 20L;
        Bukkit.getScheduler().runTaskTimer(this, this::logAndReset, ticks, ticks);
        getLogger().info("Latency probe enabled (interval=" + LOG_INTERVAL_SECONDS + "s)");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        long start = System.nanoTime();
        joinCount.increment();
        lastJoin = event.getPlayer().getName();
        record(start);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        long start = System.nanoTime();
        quitCount.increment();
        lastQuit = event.getPlayer().getName();
        record(start);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String name = event.getPlayer().getName();
        String message = event.getMessage();
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(this, () -> handleChat(name, message));
        } else {
            handleChat(name, message);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        long start = System.nanoTime();
        commandCount.increment();
        lastCommand = event.getMessage();
        record(start);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        long start = System.nanoTime();
        blockBreakCount.increment();
        lastBlockBreak = event.getBlock().getType().getKey().toString();
        record(start);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        long start = System.nanoTime();
        blockPlaceCount.increment();
        lastBlockPlace = event.getBlockPlaced().getType().getKey().toString();
        record(start);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        long start = System.nanoTime();
        deathCount.increment();
        lastDeath = event.getDeathMessage();
        record(start);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Player player)) return;
        long start = System.nanoTime();
        damageCount.increment();
        String damager = "environment";
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            damager = byEntity.getDamager().getType().getKey().toString();
        }
        lastDamage = player.getName() + ":" + event.getCause().name() + ":" + damager;
        record(start);
    }

    private void handleChat(String name, String message) {
        long start = System.nanoTime();
        chatCount.increment();
        lastChat = name + ": " + message;
        record(start);
    }

    private void record(long startNs) {
        totalEvents.increment();
        totalNanos.add(System.nanoTime() - startNs);
    }

    private void logAndReset() {
        long events = totalEvents.sumThenReset();
        long nanos = totalNanos.sumThenReset();
        long avgNs = events > 0 ? nanos / events : 0L;
        long join = joinCount.sumThenReset();
        long quit = quitCount.sumThenReset();
        long chat = chatCount.sumThenReset();
        long command = commandCount.sumThenReset();
        long breakCount = blockBreakCount.sumThenReset();
        long placeCount = blockPlaceCount.sumThenReset();
        long death = deathCount.sumThenReset();
        long damage = damageCount.sumThenReset();

        getLogger().info("[LatencyProbe] events=" + events
                + " avgNs=" + avgNs
                + " join=" + join
                + " quit=" + quit
                + " chat=" + chat
                + " cmd=" + command
                + " break=" + breakCount
                + " place=" + placeCount
                + " death=" + death
                + " damage=" + damage
        );
        getLogger().info("[LatencyProbe] last join=" + lastJoin
                + " quit=" + lastQuit
                + " chat=" + lastChat
                + " cmd=" + lastCommand
                + " break=" + lastBlockBreak
                + " place=" + lastBlockPlace
                + " death=" + lastDeath
                + " damage=" + lastDamage
        );
    }
}
