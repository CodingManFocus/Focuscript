package kr.codename.focuscript.runtime;

import kr.codename.focuscript.api.FsItemMeta;
import kr.codename.focuscript.api.FsItemStack;
import kr.codename.focuscript.api.FsText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PaperItemStackMapper {
    private PaperItemStackMapper() {}

    static ItemStack toBukkit(FsItemStack item) {
        if (item == null) return null;
        if (item instanceof PaperFsItemStack paper) {
            return paper.getHandle();
        }

        String type = item.getType();
        Material material = matchMaterial(type);
        if (material == null) {
            throw new IllegalArgumentException("Unknown material: " + type);
        }

        int amount = item.getAmount();
        if (amount < 1) {
            throw new IllegalArgumentException("Invalid item amount: " + amount);
        }

        ItemStack stack = ItemStack.of(material, amount);
        ItemMeta meta = toBukkitMeta(stack, item.getItemMeta());
        if (meta != null) {
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static FsItemStack toFs(ItemStack item) {
        if (item == null) return null;
        return new PaperFsItemStack(item);
    }

    static ItemMeta toBukkitMeta(ItemStack base, FsItemMeta meta) {
        if (meta == null) return null;
        if (meta instanceof PaperFsItemMeta paperMeta) {
            return paperMeta.getHandle();
        }
        ItemMeta itemMeta = base.getItemMeta();
        if (itemMeta == null) return null;
        applyMeta(itemMeta, meta);
        return itemMeta;
    }

    static void applyMeta(ItemMeta target, FsItemMeta meta) {
        FsText displayName = meta.getDisplayName();
        if (displayName == null) {
            target.displayName(null);
        } else {
            target.displayName(PaperTextRenderer.toComponent(displayName));
        }

        List<FsText> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            target.lore(null);
        } else {
            List<Component> comps = new ArrayList<>(lore.size());
            for (FsText line : lore) {
                if (line == null) {
                    comps.add(Component.empty());
                } else {
                    comps.add(PaperTextRenderer.toComponent(line));
                }
            }
            target.lore(comps);
        }

        if (meta.hasCustomModelData()) {
            target.setCustomModelData(meta.getCustomModelData());
        } else {
            target.setCustomModelData(null);
        }
    }

    static Material matchMaterial(String type) {
        if (type == null || type.isBlank()) return null;
        Material match = Material.matchMaterial(type);
        if (match != null) return match;
        try {
            return Material.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
