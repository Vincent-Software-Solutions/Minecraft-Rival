package de.minecraft.rival.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RivalWorldTest {
    @Test void rewritesLegacyTopLevelAndSerializedLocationWorlds() {
        assertEquals("  world: rival_main", RivalWorld.rewriteWorldLine("  world: world"));
        assertEquals("      world: rival_main", RivalWorld.rewriteWorldLine("      world: 'old_map'"));
    }

    @Test void leavesMainWorldAndUnrelatedYamlUntouched() {
        assertEquals("  world: rival_main", RivalWorld.rewriteWorldLine("  world: rival_main"));
        assertEquals("# world: old", RivalWorld.rewriteWorldLine("# world: old"));
        assertEquals("name: world", RivalWorld.rewriteWorldLine("name: world"));
    }

    @Test void projectWorldNameIsIndependentFromThePrimaryWorldName() {
        assertEquals("rival_main", RivalWorld.NAME);
    }
}
