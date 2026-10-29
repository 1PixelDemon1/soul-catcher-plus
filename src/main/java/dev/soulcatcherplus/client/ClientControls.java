// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.util.InputMappings;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** In-game shortcuts; Screens own release events after Minecraft clears KeyBinding state. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class ClientControls {
    private static final String CATEGORY = "key.categories.soulcatcherplus";
    public static final KeyBinding COLLECTION = new KeyBinding("key.soulcatcherplus.collection",
        KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
    public static final KeyBinding COMMAND_WHEEL = new KeyBinding("key.soulcatcherplus.command_wheel",
        KeyConflictContext.IN_GAME, InputMappings.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    private static boolean wheelBlockedUntilRelease;
    private static InputMappings.Input heldScanBinding;
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
        if (collection) SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.Request(SoulNetwork.Action.OPEN, null));
        else if (wheel && !wheelBlockedUntilRelease && wheelHeld()) client.setScreen(new SoulWheelScreen());
    }
    @SubscribeEvent public static void key(InputEvent.KeyInputEvent event) {
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
        InputMappings.Input key = COMMAND_WHEEL.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputMappings.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputMappings.Type.KEYSYM)
            return InputMappings.isKeyDown(window, key.getValue());
        // Scancodes have no direct GLFW polling API; matching press/release events own this state.
        return key.equals(heldScanBinding) && scanWheelDown;
    }
    private ClientControls() {}
}
