// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.network;
import java.util.function.Supplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
/** Keep client packet targets lazy so the dedicated server never loads client classes. */
final class ClientDispatch {
    static void unsafeRunWhenOn(Dist side, Supplier<Runnable> task) { if (FMLEnvironment.dist == side) task.get().run(); }
    private ClientDispatch() {}
}
