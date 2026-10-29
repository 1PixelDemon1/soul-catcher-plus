// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** In-game shortcuts; Screens own release events after Minecraft clears KeyMapping state. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class ClientControls {
    private static final String CATEGORY = "key.categories.soulcatcherplus";
    public static final KeyMapping COLLECTION = new KeyMapping("key.soulcatcherplus.collection",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
    public static final KeyMapping COMMAND_WHEEL = new KeyMapping("key.soulcatcherplus.command_wheel",
        KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    private static boolean wheelBlockedUntilRelease;
    private static InputConstants.Key heldScanBinding;
    private static boolean scanWheelDown;

    static boolean canUseMenus(Minecraft client) {
        return client.level != null && client.player != null && client.player.isAlive()
            && !client.player.isSpectator() && SoulScythe.heldBy(client.player) != null
            && client.getConnection() != null && client.isWindowActive();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
        if (client.screen != null) return;
        if (collection) SoulNetwork.sendToServer(new SoulNetwork.Request(SoulNetwork.Action.OPEN, null));
        else if (wheel && !wheelBlockedUntilRelease && wheelHeld()) client.setScreen(new SoulWheelScreen());
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!COMMAND_WHEEL.matches(event.getKey(), event.getScanCode())) return;
        if (event.getAction() == GLFW.GLFW_RELEASE) wheelTriggerReleased();
        else if (event.getAction() == GLFW.GLFW_PRESS) {
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
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM)
            return InputConstants.isKeyDown(window, key.getValue());
        // Scancodes have no direct GLFW polling API; matching press/release events own this state.
        return key.equals(heldScanBinding) && scanWheelDown;
    }
    private ClientControls() {}
}
