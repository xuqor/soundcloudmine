package xuqor.sound.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xuqor.sound.client.ui.PlayerScreen;

@Mixin(Minecraft.class)
public class OverlayMixin {
    @Inject(method="runTick", at=@At(value="INVOKE",
        target="Lcom/mojang/blaze3d/platform/Window;updateDisplay(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void soundcloudmine$overlay(boolean render, CallbackInfo ci) {
        if(Minecraft.getInstance().screen instanceof PlayerScreen screen)screen.renderOverlay();
    }
}
