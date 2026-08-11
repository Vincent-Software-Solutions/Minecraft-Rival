package de.minecraft.rival;

import de.minecraft.rival.client.RivalClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Gemeinsamer Einstiegspunkt; lädt die eigentliche Rival-Mod ausschließlich auf Clients. */
@Mod(RivalMod.MOD_ID)
public final class RivalMod {
    public static final String MOD_ID = "minecraft_rival";

    public RivalMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RivalClient::initialize);
    }
}
