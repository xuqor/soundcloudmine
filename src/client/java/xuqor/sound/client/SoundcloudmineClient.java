package xuqor.sound.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import xuqor.sound.client.api.SoundCloudApi;
import xuqor.sound.client.audio.StreamingPlayer;
import xuqor.sound.client.ui.PlayerScreen;
import java.util.concurrent.*;

public final class SoundcloudmineClient implements ClientModInitializer {
    public static final SoundCloudApi API = new SoundCloudApi();
    public static final StreamingPlayer PLAYER = new StreamingPlayer(API);
    public static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(r -> {
        Thread t=new Thread(r,"SoundCloudMine-network");t.setDaemon(true);return t;
    });
    public static Settings settings;
    @Override public void onInitializeClient() {
        settings=Settings.load();if(settings==null)settings=new Settings();
        API.setClientId(settings.clientId);PLAYER.volume(settings.volume);
        KeyMapping key=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.soundcloudmine.open",
                GLFW.GLFW_KEY_M,KeyMapping.Category.register(Identifier.fromNamespaceAndPath("soundcloudmine","player"))));
        ScreenEvents.AFTER_INIT.register((mc, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                screen.addRenderableWidget(Button.builder(net.minecraft.network.chat.Component.translatable("key.soundcloudmine.open"),
                        button -> mc.setScreen(new PlayerScreen(screen)))
                        .bounds(scaledWidth / 2 - 100, scaledHeight / 4 + 120, 200, 20).build());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while(key.consumeClick()) if(!(mc.screen instanceof PlayerScreen)) mc.setScreen(new PlayerScreen(mc.screen));
            if(Boolean.getBoolean("soundcloudmine.preview") && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen)
                mc.setScreen(new PlayerScreen(mc.screen));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            settings.volume=PLAYER.volume();settings.save();PLAYER.close();NETWORK.shutdownNow();PlayerScreen.destroyRenderer();
        });
    }
}
