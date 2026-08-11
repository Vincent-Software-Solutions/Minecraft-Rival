package de.minecraft.rival.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StatePayload(byte[] data) implements CustomPacketPayload {
    public static final Type<StatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("rival", "state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeBytes(payload.data),
        buffer -> {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new StatePayload(bytes);
        });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
