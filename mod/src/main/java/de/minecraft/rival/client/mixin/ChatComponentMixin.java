package de.minecraft.rival.client.mixin;

import de.minecraft.rival.client.RivalClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tracks the real visibility window of every normal chat message. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void rival$noteChatMessage(Component message, CallbackInfo callback) {
        RivalClient.noteChatMessage();
    }
}
