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
        data.commandMode = true; data.commandGroup = false;
        data.selected = entry.id; data.entries.put(entry.id, entry);
        ledger.save(root);
        // Old ledger energy is intentionally ignored; collection ownership still survives.
        root.getCompoundOrEmpty("Players").getCompoundOrEmpty(owner.toString()).putInt("Energy", 91);
        PlayerSouls restored = SoulLedger.load(root).player(owner);
        assertFalse(restored.save().contains("Energy"));
        assertEquals(entry.id, restored.selected);
        assertTrue(restored.commandMode); assertFalse(restored.commandGroup);
        assertEquals(active, restored.entries.get(entry.id).activeEntity);
        assertEquals(1200, restored.entries.get(entry.id).readyAt);
        assertEquals("minecraft:the_nether", restored.entries.get(entry.id).dimension);
        assertEquals(1, restored.activeCount());
    }
    @Test void sanitizeDropsDeathIdentityAndOurOwnershipButKeepsModState() {
        CompoundTag source = new CompoundTag(), forge = new CompoundTag();
        SoulNbt.putUuid(source, "UUID", UUID.randomUUID()); source.putFloat("Health", 0);
        source.putInt("DeathTime", 40); source.putString("CustomAbility", "fireball");
        SoulNbt.putUuid(forge, "scp_owner", UUID.randomUUID()); forge.putString("othermod_state", "charged");
        source.put("ForgeData", forge);
        CompoundTag clean = SoulSnapshots.sanitize(source);
        assertFalse(SoulNbt.hasUuid(clean,"UUID")); assertFalse(clean.contains("Health"));
        assertFalse(SoulNbt.hasUuid(clean.getCompoundOrEmpty("ForgeData"),"scp_owner"));
        assertEquals("charged", clean.getCompoundOrEmpty("ForgeData").getStringOr("othermod_state",""));
        assertEquals("fireball", clean.getStringOr("CustomAbility",""));
        assertEquals(0, clean.getIntOr("DeathTime",0));
        assertTrue(SoulNbt.hasUuid(source,"UUID")); // Input is never mutated.
        assertTrue(SoulNbt.hasUuid(source.getCompoundOrEmpty("ForgeData"),"scp_owner"));
    }
    @Test void snapshotsAreDefensiveCopies() {
        CompoundTag snapshot = new CompoundTag(); snapshot.putString("Variant", "original");
        SoulEntry entry = new SoulEntry(UUID.randomUUID(), "example:mage", "Mage", snapshot, 5);
        snapshot.putString("Variant", "changed");
        entry.snapshot().putString("Variant", "changed again");
        assertEquals("original", entry.snapshot().getStringOr("Variant",""));
    }
}
