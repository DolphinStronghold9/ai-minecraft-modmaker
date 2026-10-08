package dev.mortar.paper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server side of Mortar. Ghosts are sent only to the player who asked, as glass in
 * the shape of the build, with Player#sendBlockChange. Nothing in the world changes
 * until they say yes in the terminal, and only operators can place.
 */
public final class MortarPlugin extends JavaPlugin implements Listener {
    private static final BlockData GHOST = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();
    /** Blocks placed per tick, so large builds never stall the server. */
    private static final int PER_TICK = 4096;

    private final Map<UUID, List<Location>> ghosts = new HashMap<>();
    private CompanionLink link;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        link = new CompanionLink(this, getConfig().getInt("port", 44878));
        link.start();
    }

    @Override
    public void onDisable() {
        ghosts.keySet().forEach(id -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) clearGhost(player);
        });
        if (link != null) link.stop();
    }

    public void showGhost(Player player, List<Location> blocks) {
        clearGhost(player);
        for (Location at : blocks) player.sendBlockChange(at, GHOST);
        ghosts.put(player.getUniqueId(), new ArrayList<>(blocks));
    }

    public void clearGhost(Player player) {
        List<Location> shown = ghosts.remove(player.getUniqueId());
        if (shown == null) return;
        for (Location at : shown) player.sendBlockChange(at, at.getBlock().getBlockData());
    }

    /** Places a batch for an operator. Returns false if they aren't allowed to. */
    public boolean place(Player player, Map<Location, BlockData> blocks) {
        if (!player.isOp()) return false;
        clearGhost(player);
        List<Map.Entry<Location, BlockData>> sorted = new ArrayList<>(blocks.entrySet());
        sorted.sort((a, b) -> Integer.compare(a.getKey().getBlockY(), b.getKey().getBlockY()));
        ArrayDeque<Map.Entry<Location, BlockData>> queue = new ArrayDeque<>(sorted);
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            for (int i = 0; i < PER_TICK && !queue.isEmpty(); i++) {
                var next = queue.poll();
                next.getKey().getBlock().setBlockData(next.getValue(), false);
            }
            if (queue.isEmpty()) task.cancel();
        }, 1L, 1L);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ghosts.remove(event.getPlayer().getUniqueId());
    }
}
