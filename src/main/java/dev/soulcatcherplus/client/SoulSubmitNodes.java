// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.*;

/** Models and custom geometry share the captured tint; native effects keep their own submissions. */
final class SoulSubmitNodes implements SubmitNodeCollector {
    private final SubmitNodeCollector root;
    private final OrderedSubmitNodeCollector delegate;
    private final SoulVisuals.Appearance appearance;
    SoulSubmitNodes(SubmitNodeCollector root,SoulVisuals.Appearance appearance) { this(root,root,appearance); }
    private SoulSubmitNodes(SubmitNodeCollector root,OrderedSubmitNodeCollector delegate,SoulVisuals.Appearance appearance) {
        this.root=root;this.delegate=delegate;this.appearance=appearance;
    }
    SubmitNodeCollector root() { return root; }
    @Override public OrderedSubmitNodeCollector order(int order) { return new SoulSubmitNodes(root,root.order(order),appearance); }
    @Override public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        delegate.submitShadow(poseStack,radius,pieces);
    }
    @Override public void submitNameTag(
        PoseStack poseStack,
        @Nullable Vec3 nameTagAttachment,
        final int offset,
        Component name,
        boolean seeThrough,
        int lightCoords,
        final CameraRenderState camera
    ) {
        delegate.submitNameTag(poseStack,nameTagAttachment,offset,name,seeThrough,lightCoords,camera);
    }
    @Override public void submitText(
        PoseStack poseStack,
        float x,
        float y,
        FormattedCharSequence string,
        boolean dropShadow,
        Font.DisplayMode displayMode,
        int lightCoords,
        int color,
        int backgroundColor,
        int outlineColor
    ) {
        delegate.submitText(poseStack,x,y,string,dropShadow,displayMode,lightCoords,color,backgroundColor,outlineColor);
    }
    @Override public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode, int lightCoords) {
        delegate.submitTextBackground(poseStack,x0,y0,x1,y1,color,displayMode,lightCoords);
    }
    @Override public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        delegate.submitFlame(poseStack,renderState,rotation);
    }
    @Override public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        delegate.submitLeash(poseStack,leashState);
    }
    @Override public <S> void submitModel(
        Model<? super S> model,
        S state,
        PoseStack poseStack,
        RenderType renderType,
        int lightCoords,
        int overlayCoords,
        int tintedColor,
        @Nullable UvMapping uvMapping,
        int outlineColor
    ) {
        RenderType spectral=SoulVisuals.remap(appearance,renderType);
        if(appearance.fade()==null) {
            delegate.submitModel(model,state,poseStack,spectral,lightCoords,overlayCoords,SoulVisuals.color(appearance,tintedColor),uvMapping,outlineColor);
        } else {
            delegate.submitCustomGeometry(poseStack,spectral,(frame,buffer)->{
                model.setupAnim(state);
                var output=SoulVisuals.tint(buffer,appearance);
                if(uvMapping!=null) output=uvMapping.wrap(output);
                model.renderToBuffer(SoulModelRenderer.poseFrom(frame),output,lightCoords,overlayCoords,tintedColor);
            });
        }
    }
    @Override public <S> void submitCrumblingOverlay(
        Model<? super S> model,
        S state,
        PoseStack poseStack,
        RenderType renderType,
        int lightCoords,
        int overlayCoords,
        int tintedColor,
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        delegate.submitCrumblingOverlay(model,state,poseStack,renderType,lightCoords,overlayCoords,tintedColor,crumblingOverlay);
    }
    @Override public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        delegate.submitMovingBlock(poseStack,movingBlockRenderState,outlineColor);
    }
    @Override public void submitBlockModel(
        PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor
    ) {
        delegate.submitBlockModel(poseStack,renderType,parts,tintLayers,lightCoords,overlayCoords,outlineColor);
    }
    @Override public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, final boolean isBlockTranslucent) {
        delegate.submitBreakingBlockModel(poseStack,parts,progress,isBlockTranslucent);
    }
    @Override public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
        delegate.submitShapeOutline(poseStack,shape,renderType,color,width,afterTerrain);
    }
    @Override public void submitItem(
        PoseStack poseStack,
        ItemDisplayContext displayContext,
        int lightCoords,
        int overlayCoords,
        int outlineColor,
        int[] tintLayers,
        ItemQuads quads,
        ItemStackRenderState.FoilType foilType
    ) {
        if(outlineColor!=0) for(var quad:quads.all()) {
            quad.materialInfo().itemRenderType().outline().ifPresent(outline ->
                delegate.submitCustomGeometry(poseStack,outline,(frame,buffer)->{
                    var instance=new com.mojang.blaze3d.vertex.QuadInstance();
                    instance.setColor(outlineColor);
                    buffer.putBakedQuad(frame,quad,instance);
                }));
        }
        // ItemFeatureRenderer has a separate quad queue; replay those same quads with captured appearance.
        int[] colors=tintLayers.clone();
        var groups=new java.util.LinkedHashMap<RenderType,java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad>>();
        for(var quad:quads.all()) {
            var texture=quad.materialInfo().sprite().atlasLocation();
            RenderType type=switch(foilType) {
                case NONE -> net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucent(texture);
                case STANDARD -> net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucentGlint(texture);
                case SPECIAL -> net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucentGlintSpecial(texture);
            };
            groups.computeIfAbsent(type,key->new java.util.ArrayList<>()).add(quad);
        }
        for(var group:groups.entrySet()) delegate.submitCustomGeometry(poseStack,group.getKey(),(frame,buffer)->{
            var output=SoulVisuals.tint(buffer,appearance);
            var instance=new com.mojang.blaze3d.vertex.QuadInstance();
            instance.setLightCoords(lightCoords);instance.setOverlayCoords(overlayCoords);
            PoseStack.Pose decal=frame.copy();
            if(displayContext==ItemDisplayContext.GUI) com.mojang.math.MatrixUtil.mulComponentWise(decal.pose(),.5f);
            else if(displayContext.firstPerson()) com.mojang.math.MatrixUtil.mulComponentWise(decal.pose(),.75f);
            for(var quad:group.getValue()) {
                int index=quad.materialInfo().tintIndex();
                instance.setColor(index>=0 && index<colors.length?colors[index]:-1);
                if(foilType==ItemStackRenderState.FoilType.SPECIAL) output.putBakedQuadWithGlint(frame,quad,instance,decal);
                else output.putBakedQuad(frame,quad,instance);
            }
        });
    }
    @Override public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        delegate.submitCustomGeometry(poseStack,SoulVisuals.remap(appearance,renderType),
            (pose,buffer)->customGeometryRenderer.render(pose,SoulVisuals.tint(buffer,appearance)));
    }
    @Override public void submitQuadParticleGroup(QuadParticleRenderState particles) {
        delegate.submitQuadParticleGroup(particles);
    }
    @Override public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
        delegate.submitGizmoPrimitives(group,camera,onTop);
    }
}
