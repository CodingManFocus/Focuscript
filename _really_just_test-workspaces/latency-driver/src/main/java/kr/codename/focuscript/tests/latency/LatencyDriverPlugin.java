package kr.codename.focuscript.tests.latency;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class LatencyDriverPlugin extends JavaPlugin {
    private long intervalTicks;
    private boolean fireJoinQuit;
    private boolean fireChat;
    private boolean fireCommand;
    private boolean fireBlock;
    private boolean fireDamage;
    private boolean asyncChat;
    private String chatMessage;
    private String commandLine;
    private Material blockMaterial;
    private int blockOffsetX;
    private int blockOffsetY;
    private int blockOffsetZ;
    private double damageAmount;

    private long sequence;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        Bukkit.getScheduler().runTaskTimer(this, this::tick, intervalTicks, intervalTicks);
        getLogger().info("Latency driver enabled (intervalTicks=" + intervalTicks + ")");
    }

    private void loadSettings() {
        intervalTicks = Math.max(1L, getConfig().getLong("intervalTicks", 20L));
        fireJoinQuit = getConfig().getBoolean("fireJoinQuit", false);
        fireChat = getConfig().getBoolean("fireChat", true);
        fireCommand = getConfig().getBoolean("fireCommand", true);
        fireBlock = getConfig().getBoolean("fireBlock", true);
        fireDamage = getConfig().getBoolean("fireDamage", true);
        asyncChat = getConfig().getBoolean("asyncChat", true);
        chatMessage = getConfig().getString("chatMessage", "latency-probe");
        commandLine = getConfig().getString("commandLine", "/say latency-probe");
        blockMaterial = parseMaterial(getConfig().getString("blockMaterial", "STONE"));
        damageAmount = Math.max(0.0, getConfig().getDouble("damageAmount", 0.1));

        ConfigurationSection offset = getConfig().getConfigurationSection("blockOffset");
        if (offset != null) {
            blockOffsetX = offset.getInt("x", 2);
            blockOffsetY = offset.getInt("y", 0);
            blockOffsetZ = offset.getInt("z", 0);
        } else {
            blockOffsetX = 2;
            blockOffsetY = 0;
            blockOffsetZ = 0;
        }
    }

    private void tick() {
        Player player = pickPlayer();
        if (player == null) return;

        sequence++;
        if (fireJoinQuit) {
            Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(player, ""));
            Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player, ""));
        }

        if (fireCommand) {
            String cmd = commandLine;
            if (!cmd.startsWith("/")) {
                cmd = "/" + cmd;
            }
            Bukkit.getPluginManager().callEvent(new PlayerCommandPreprocessEvent(player, cmd));
        }

        if (fireBlock) {
            fireBlockEvents(player);
        }

        if (fireDamage) {
            EntityDamageEvent event = new EntityDamageByEntityEvent(
                    player,
                    player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    damageAmount
            );
            Bukkit.getPluginManager().callEvent(event);
        }

        if (fireChat) {
            String message = chatMessage + " #" + sequence;
            if (asyncChat) {
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> fireChat(player, message, true));
            } else {
                fireChat(player, message, false);
            }
        }
    }

    private void fireChat(Player player, String message, boolean async) {
        Set<Player> recipients = new HashSet<>(Bukkit.getOnlinePlayers());
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(async, player, message, recipients);
        Bukkit.getPluginManager().callEvent(event);
    }

    private void fireBlockEvents(Player player) {
        Location base = player.getLocation().clone().add(blockOffsetX, blockOffsetY, blockOffsetZ);
        World world = base.getWorld();
        if (world == null) return;

        Block block = world.getBlockAt(base);
        Material before = block.getType();
        BlockState replaced = block.getState();

        block.setType(blockMaterial, false);
        Bukkit.getPluginManager().callEvent(new BlockBreakEvent(block, player));
        Bukkit.getPluginManager().callEvent(new BlockPlaceEvent(
                block,
                replaced,
                block,
                new ItemStack(blockMaterial),
                player,
                true,
                EquipmentSlot.HAND
        ));

        block.setType(before, false);
    }

    private Player pickPlayer() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            return player;
        }
        return null;
    }

    private static Material parseMaterial(String value) {
        if (value == null || value.isBlank()) return Material.STONE;
        Material material = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        return material == null ? Material.STONE : material;
    }
}
