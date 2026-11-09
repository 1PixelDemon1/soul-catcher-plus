// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/** In-game shortcuts; Screens own release events after Minecraft clears KeyMapping state. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class ClientControls {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(SoulCatcherPlus.ID,"controls"));
    public static final KeyMapping COLLECTION = new KeyMapping("key.soulcatcherplus.collection",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_O, CATEGORY, 0);
    public static final KeyMapping COMMAND_WHEEL = new KeyMapping("key.soulcatcherplus.command_wheel",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY, 1);
    private static boolean wheelBlockedUntilRelease;
    private static InputConstants.Key heldScanBinding;
    private static boolean scanWheelDown;

    static boolean canUseMenus(Minecraft client) {
        return client.level != null && client.player != null && client.player.isAlive()
            && !client.player.isSpectator() && SoulScythe.heldBy(client.player) != null
            && client.getConnection() != null && client.isWindowActive();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        boolean collection = false, wheel = false;
        while (COLLECTION.consumeClick()) collection = true;
        while (COMMAND_WHEEL.consumeClick()) wheel = true;
        if (!canUseMenus(client)) {
            scanWheelDown = false;
            wheelBlockedUntilRelease = false;
            return;
        }
        if (wheelBlockedUntilRelease && !wheelHeld()) wheelBlockedUntilRelease = false;
        if (client.gui.screen() != null) return;
        if (collection) SoulNetwork.sendToServer(new SoulNetwork.Request(SoulNetwork.Action.OPEN, null));
        else if (wheel && !wheelBlockedUntilRelease && wheelHeld()) client.gui.setScreen(new SoulWheelScreen());
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!COMMAND_WHEEL.matches(event.getInfo())) return;
        if (event.getAction() == InputConstants.RELEASE) wheelTriggerReleased();
        else if (event.getAction() == InputConstants.PRESS) {
            heldScanBinding = COMMAND_WHEEL.getKey();
            scanWheelDown = true;
        }
    }
    static void wheelTriggerReleased() {
        scanWheelDown = false;
        wheelBlockedUntilRelease = false;
    }
    static void wheelClosed() { wheelBlockedUntilRelease = true; }
    static boolean wheelHeld() {
        if (COMMAND_WHEEL.isUnbound()) return false;
        InputConstants.Key key = COMMAND_WHEEL.getKey();
        if(key.getType()==InputConstants.Type.KEYBOARD) return InputConstants.isKeyDown(key.getValue());
        return key.equals(heldScanBinding) && scanWheelDown;
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Post event) {
        var key=InputConstants.Type.MOUSE.getOrCreate(event.getInfo().button());
        if(!COMMAND_WHEEL.matches(key)) return;
        if(event.getAction()==InputConstants.RELEASE) wheelTriggerReleased();
        else if(event.getAction()==InputConstants.PRESS) { heldScanBinding=key;scanWheelDown=true; }
    }

    private ClientControls() {}
}
