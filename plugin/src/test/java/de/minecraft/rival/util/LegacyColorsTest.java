package de.minecraft.rival.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyColorsTest {
    @Test void convertsEveryHexStopAndStyleInTheRivalPrefix() {
        String input = "&#FF0000&lR&#FD462B&lI&#FA8B56&lV&#FCA359&lA&#FDBB5B&lL&#FFD35E&lS &r&8&l➜ ";
        String expected = "§x§F§F§0§0§0§0§lR§x§F§D§4§6§2§B§lI§x§F§A§8§B§5§6§lV"
            + "§x§F§C§A§3§5§9§lA§x§F§D§B§B§5§B§lL§x§F§F§D§3§5§E§lS §r§8§l➜ ";
        assertEquals(expected, LegacyColors.translate(input));
    }

    @Test void preservesPlainTextAndConvertsLegacyColors() {
        assertEquals("Text §6Wert §cFehler", LegacyColors.translate("Text &6Wert &cFehler"));
        assertEquals("A &z B", LegacyColors.translate("A &z B"));
        assertEquals("", LegacyColors.translate(null));
    }
}
