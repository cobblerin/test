package kr.cobblerin.pokedex;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static kr.cobblerin.pokedex.DexProtocol.*;

public final class DexClient implements ClientModInitializer {
    private static final Gson GSON = new Gson();
    private static int sequence;

    @Override
    public void onInitializeClient() {
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cobblemon_donation_pokedex.open", GLFW.GLFW_KEY_K, "key.categories.cobblemon_donation_pokedex"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    if (ClientPlayNetworking.canSend(Request.ID)) {
                        var screen = new DexScreen();
                        client.setScreen(screen);
                        screen.refresh();
                    } else {
                        client.player.sendMessage(Text.literal("이 서버에서 납부 도감 HUD를 사용할 수 없습니다."), false);
                    }
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(Response.ID, (payload, context) -> {
            var client = context.client();
            if (payload.sequence() != 0 && !(client.currentScreen instanceof DexScreen)) {
                return;
            }
            try {
                View view = GSON.fromJson(payload.json(), View.class);
                if (client.currentScreen instanceof DexScreen screen) {
                    screen.accept(payload.sequence(), view);
                } else if (payload.sequence() == 0) {
                    openFromCommand(client, view);
                }
            } catch (RuntimeException exception) {
                DonationPokedex.LOG.warn("Could not read Pokédex HUD response", exception);
            }
        });
    }

    private static void openFromCommand(MinecraftClient client, View view) {
        if (view.overview() == null) {
            if (client.player != null && view.message() != null) {
                client.player.sendMessage(Text.literal(view.message()), false);
            }
            return;
        }
        var screen = new DexScreen();
        client.setScreen(screen);
        screen.accept(0, view);
    }

    static int request(String action, int species, int page, String value) {
        if (!ClientPlayNetworking.canSend(Request.ID)) {
            return 0;
        }
        sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
        ClientPlayNetworking.send(new Request(sequence, action, species, page, value));
        return sequence;
    }
}
