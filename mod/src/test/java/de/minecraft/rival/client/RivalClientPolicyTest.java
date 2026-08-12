package de.minecraft.rival.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RivalClientPolicyTest {
    @Test
    void stripsLocalizedDisconnectTitleFromVisibleError() {
        assertEquals("Dieser Server ist nicht zugelassen.",
            RivalScreenStyle.cleanReason("Verbindung unterbrochen Dieser Server ist nicht zugelassen.", "Verbindung unterbrochen"));
        assertEquals("Timed out", RivalScreenStyle.cleanReason("Connection Lost: Timed out", "Connection Lost"));
    }
}
