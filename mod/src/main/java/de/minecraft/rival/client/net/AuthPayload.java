package de.minecraft.rival.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record AuthPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<AuthPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("rival", "auth"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuthPayload> CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeBytes(payload.data),
        buffer -> {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new AuthPayload(bytes);
        });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
