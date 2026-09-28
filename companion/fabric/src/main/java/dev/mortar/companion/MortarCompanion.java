package dev.mortar.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client companion for the mortar terminal agent.
 *
 * It answers "look" requests with the blocks around the player and draws the
 * ghost of a build that hasn't been placed yet. It never places blocks itself:
 * mortar places them with the same commands an operator could type, so the
 * game's own permissions always apply.
 */
public final class MortarCompanion implements ClientModInitializer {
    private static final URI MORTAR = URI.create("ws://127.0.0.1:44878/companion");
    private static final float[] GHOST = {0.37f, 0.82f, 0.78f};

    private final Map<BlockPos, String> ghost = new ConcurrentHashMap<>();
    private volatile WebSocket socket;
    private int retryTicks;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (socket == null && client.level != null && --retryTicks <= 0) {
                retryTicks = 40;
                connect();
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (ghost.isEmpty()) return;
            Vec3 cam = context.camera().getPosition();
            var pose = context.matrixStack();
            var lines = context.consumers().getBuffer(RenderType.lines());
            for (BlockPos pos : ghost.keySet()) {
                AABB box = new AABB(pos).move(-cam.x, -cam.y, -cam.z).deflate(0.002);
                ShapeRenderer.renderLineBox(pose.last(), lines, box, GHOST[0], GHOST[1], GHOST[2], 0.9f);
            }
        });
    }

    private void connect() {
        HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(MORTAR, new Listener())
            .thenAccept(ws -> {
                socket = ws;
                send(hello());
            })
            .exceptionally(error -> null);
    }

    private void send(JsonObject message) {
        WebSocket ws = socket;
        if (ws != null) ws.sendText(message.toString(), true);
    }

    private JsonObject hello() {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("game", "java");
        hello.addProperty("version", "26.3");
        return hello;
    }

    /** Everything that isn't air within `radius` blocks of the player, plus where they look. */
    private JsonObject look(int radius) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos center = mc.player.blockPosition();
        JsonArray blocks = new JsonArray();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), center.offset(radius, radius, radius))) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) continue;
            JsonArray entry = new JsonArray();
            entry.add(pos.getX());
            entry.add(pos.getY());
            entry.add(pos.getZ());
            entry.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            blocks.add(entry);
        }
        JsonObject reply = new JsonObject();
        reply.addProperty("type", "area");
        reply.add("blocks", blocks);
        reply.addProperty("biome", mc.level.getBiome(center).getRegisteredName());
        reply.addProperty("player", center.toShortString());
        return reply;
    }

    private void handle(JsonObject message) {
        switch (message.get("type").getAsString()) {
            case "look" -> Minecraft.getInstance().execute(() -> send(look(message.get("radius").getAsInt())));
            case "ghost" -> {
                ghost.clear();
                for (var entry : message.getAsJsonArray("blocks")) {
                    JsonArray b = entry.getAsJsonArray();
                    ghost.put(new BlockPos(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()), b.get(3).getAsString());
                }
            }
            case "clear" -> ghost.clear();
            default -> { }
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                handle(JsonParser.parseString(partial.toString()).getAsJsonObject());
                partial.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            socket = null;
            ghost.clear();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            socket = null;
        }
    }
}
