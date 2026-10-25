// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoulPersistenceTest {
    @Test void collectionSurvivesSaveWithActiveIdentityAndCooldown() {
        UUID owner = UUID.randomUUID(), active = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        SoulLedger ledger = new SoulLedger();
        PlayerSouls data = ledger.player(owner);
        SoulEntry entry = new SoulEntry(UUID.randomUUID(), "minecraft:skeleton", "Archer", new CompoundTag(), 15);
        entry.activeEntity = active; entry.dimension = "minecraft:the_nether"; entry.readyAt = 1200;
        data.energy = 91; data.commandMode = true; data.commandGroup = false;
        data.selected = entry.id; data.entries.put(entry.id, entry);
        PlayerSouls restored = SoulLedger.load(ledger.save(root)).player(owner);
        assertEquals(91, restored.energy);
        assertEquals(entry.id, restored.selected);
        assertTrue(restored.commandMode); assertFalse(restored.commandGroup);
        assertEquals(active, restored.entries.get(entry.id).activeEntity);
        assertEquals(1200, restored.entries.get(entry.id).readyAt);
        assertEquals("minecraft:the_nether", restored.entries.get(entry.id).dimension);
        assertEquals(1, restored.activeCount());
    }
    @Test void sanitizeDropsDeathIdentityAndOurOwnershipButKeepsModState() {
        CompoundTag source = new CompoundTag(), forge = new CompoundTag();
        source.putUUID("UUID", UUID.randomUUID()); source.putFloat("Health", 0);
        source.putInt("DeathTime", 40); source.putString("CustomAbility", "fireball");
        forge.putUUID("scp_owner", UUID.randomUUID()); forge.putString("othermod_state", "charged");
        source.put("ForgeData", forge);
        CompoundTag clean = SoulSnapshots.sanitize(source);
        assertFalse(clean.hasUUID("UUID")); assertFalse(clean.contains("Health"));
        assertFalse(clean.getCompound("ForgeData").hasUUID("scp_owner"));
        assertEquals("charged", clean.getCompound("ForgeData").getString("othermod_state"));
        assertEquals("fireball", clean.getString("CustomAbility"));
        assertEquals(0, clean.getInt("DeathTime"));
        assertTrue(source.hasUUID("UUID")); // Input is never mutated.
        assertTrue(source.getCompound("ForgeData").hasUUID("scp_owner"));
    }
    @Test void snapshotsAreDefensiveCopies() {
        CompoundTag snapshot = new CompoundTag(); snapshot.putString("Variant", "original");
        SoulEntry entry = new SoulEntry(UUID.randomUUID(), "example:mage", "Mage", snapshot, 5);
        snapshot.putString("Variant", "changed");
        entry.snapshot().putString("Variant", "changed again");
        assertEquals("original", entry.snapshot().getString("Variant"));
    }
}
