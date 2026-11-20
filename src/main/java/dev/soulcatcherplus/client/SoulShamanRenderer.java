// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.model.SoulShamanModel;
import dev.soulcatcherplus.entity.SoulShamanEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.resources.Identifier;

public final class SoulShamanRenderer extends SoulModelRenderer<SoulShamanEntity> {
    private static final SoulShamanModel.Material[] MATERIALS=SoulShamanModel.Material.values();
    private static final Identifier ALTERNATE = Identifier.fromNamespaceAndPath("soulcatcherplus", "textures/entity/soul_shaman_miku.png");
    private final SoulShamanModel model = new SoulShamanModel(false);
    private final SoulShamanModel miku = new SoulShamanModel(true);
    public SoulShamanRenderer(EntityRendererProvider.Context context) { super(context,.3f); }
    @Override public void extractRenderState(SoulShamanEntity entity,SoulModelState state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.alternateForm = entity.isMiku();state.castPhase=entity.castPhase(state.ageInTicks);
    }
    @Override protected void submitModel(SoulModelState state, PoseStack pose, SubmitNodeCollector nodes) {
        SoulShamanModel active = state.alternateForm ? miku : model;
        for (var material : MATERIALS) {
            if (state.alternateForm && material != SoulShamanModel.Material.CLOTH && material != SoulShamanModel.Material.SOUL_LIGHT)
                continue;
            Identifier texture = state.alternateForm ? ALTERNATE : material.texture;
            nodes.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (frame, buffer) -> {
                active.setupAnim(state.castPhase, state.ageInTicks, state.headYaw, state.pitch);
                active.render(material, poseFrom(frame), buffer, material.emissive ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords,
                    state.overlay, 1, 1, 1, 1);
            });
        }
    }
}
