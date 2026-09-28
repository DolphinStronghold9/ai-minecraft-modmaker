package dev.mortar.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/** The connection to mortar on this machine. Messages are handled on the main thread. */
final class CompanionLink implements WebSocket.Listener {
    private final MortarPlugin plugin;
    private final URI uri;
    private final StringBuilder partial = new StringBuilder();
    private volatile WebSocket socket;
    private int retryTask = -1;

    CompanionLink(MortarPlugin plugin, int port) {
        this.plugin = plugin;
        this.uri = URI.create("ws://127.0.0.1:" + port + "/companion");
    }

    void start() {
        retryTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (socket == null) {
                HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, this)
                    .thenAccept(ws -> socket = ws)
                    .exceptionally(error -> null);
            }
        }, 20L, 100L).getTaskId();
    }

    void stop() {
        if (retryTask != -1) Bukkit.getScheduler().cancelTask(retryTask);
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "server stopping");
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            JsonObject message = JsonParser.parseString(partial.toString()).getAsJsonObject();
            partial.setLength(0);
            Bukkit.getScheduler().runTask(plugin, () -> handle(message));
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
        socket = null;
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        socket = null;
    }

    private void handle(JsonObject message) {
        Player player = Bukkit.getPlayerExact(message.get("player").getAsString());
        if (player == null) return;
        World world = player.getWorld();
        switch (message.get("type").getAsString()) {
            case "ghost" -> {
                List<Location> blocks = new ArrayList<>();
                for (var e : message.getAsJsonArray("blocks")) {
                    var b = e.getAsJsonArray();
                    blocks.add(new Location(world, b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()));
                }
                plugin.showGhost(player, blocks);
            }
            case "clear" -> plugin.clearGhost(player);
            case "place" -> {
                Map<Location, BlockData> blocks = new HashMap<>();
                for (var e : message.getAsJsonArray("blocks")) {
                    var b = e.getAsJsonArray();
                    blocks.put(new Location(world, b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()),
                        Bukkit.createBlockData(b.get(3).getAsString()));
                }
                boolean ok = plugin.place(player, blocks);
                JsonObject reply = new JsonObject();
                reply.addProperty("type", ok ? "placed" : "denied");
                reply.addProperty("id", message.get("id").getAsString());
                if (socket != null) socket.sendText(reply.toString(), true);
            }
            default -> { }
        }
    }
}
