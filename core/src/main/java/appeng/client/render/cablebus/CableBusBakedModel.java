/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.render.cablebus;

import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Supplier;

public class CableBusBakedModel implements BakedModel, FabricBakedModel {

    // FIXME: This entire cache seems dumb as shit
    private static final Map<CableBusRenderState, Mesh> CABLE_MODEL_CACHE = new HashMap<>();

    private final CableBuilder cableBuilder;

    private final FacadeBuilder facadeBuilder;

    private final Map<Identifier, BakedModel> partModels;

    private final Sprite particleTexture;

    CableBusBakedModel(CableBuilder cableBuilder, FacadeBuilder facadeBuilder,
                       Map<Identifier, BakedModel> partModels, Sprite particleTexture) {
        this.cableBuilder = cableBuilder;
        this.facadeBuilder = facadeBuilder;
        this.partModels = partModels;
        this.particleTexture = particleTexture;
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context) {
        // This model will only ever be used for blocks
    }

    private CableBusRenderState getRenderState(BlockRenderView blockView, BlockPos pos) {

        RenderAttachedBlockView renderAttachedBlockView = (RenderAttachedBlockView) blockView;
        Object renderAttachment = renderAttachedBlockView.getBlockEntityRenderAttachment(pos);
        if (renderAttachment instanceof CableBusRenderState) {
            return (CableBusRenderState) renderAttachment;
        }
        return null;

    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {

        CableBusRenderState renderState = getRenderState(blockView, pos);

        if (renderState == null) {
            return;
        }

        RenderLayer layer = RenderLayer.getCutout(); // FIXME: Fabric can only render within one layer (?)

        // The core parts of the cable will only be rendered in the CUTOUT layer.
        // Facades will add them selves to what ever the block would be rendered with,
        // except when transparent facades are enabled, they are forced to TRANSPARENT.
        if (layer == RenderLayer.getCutout()) {
            // First, handle the cable at the center of the cable bus
            final Mesh cableModel = CABLE_MODEL_CACHE.computeIfAbsent(renderState, this::buildCableModel);
            if (cableModel != null) {
                context.meshConsumer().accept(cableModel);
            }

            // Then handle attachments
            for (Direction facing : Direction.values()) {
                final IPartModel partModel = renderState.getAttachments().get(facing);
                if (partModel == null) {
                    continue;
                }

                Object partModelData = renderState.getPartModelData().get(facing);

                for (Identifier model : partModel.getModels()) {
                    BakedModel bakedModel = this.partModels.get(model);

                    if (bakedModel == null) {
                        throw new IllegalStateException("Trying to use an unregistered part model: " + model);
                    }

                    context.pushTransform(QuadRotator.get(facing, Direction.UP));
                    if (bakedModel instanceof FabricBakedModel) {
                        ((FabricBakedModel) bakedModel).emitBlockQuads(blockView, state, pos, randomSupplier, context);
                    } else {
                        context.fallbackConsumer().accept(bakedModel);
                    }
                    context.popTransform();
                }
            }
        }

        this.facadeBuilder.buildFacadeQuads(layer, renderState, randomSupplier, context, this.partModels::get);
    }

    // Determines whether a cable is connected to exactly two sides that are
    // opposite each other
    private static boolean isStraightLine(AECableType cableType, EnumMap<Direction, AECableType> sides) {
        final Iterator<Entry<Direction, AECableType>> it = sides.entrySet().iterator();
        if (!it.hasNext()) {
            return false; // No connections
        }

        final Entry<Direction, AECableType> nextConnection = it.next();
        final Direction firstSide = nextConnection.getKey();
        final AECableType firstType = nextConnection.getValue();

        if (!it.hasNext()) {
            return false; // Only a single connection
        }
        if (firstSide.getOpposite() != it.next().getKey()) {
            return false; // Connected to two sides that are not opposite each other
        }
        if (it.hasNext()) {
            return false; // Must not have any other connection points
        }

        final AECableType secondType = sides.get(firstSide.getOpposite());

        return firstType == secondType && cableType == firstType && cableType == secondType;
    }

    private Mesh buildCableModel(CableBusRenderState renderState) {
        AECableType cableType = renderState.getCableType();
        if (cableType == AECableType.NONE) {
            return null;
        }

        AEColor cableColor = renderState.getCableColor();
        EnumMap<Direction, AECableType> connectionTypes = renderState.getConnectionTypes();

        MeshBuilder builder = IndigoRenderer.INSTANCE.meshBuilder();
        QuadEmitter emitter = builder.getEmitter();
// FIXME
// FIXME        // If the connection is straight, no busses are attached, and no covered core
// FIXME        // has been forced (in case of glass
// FIXME        // cables), then render the cable as a simplified straight line.
// FIXME        boolean noAttachments = !renderState.getAttachments().values().stream()
// FIXME                .anyMatch(IPartModel::requireCableConnection);
// FIXME        if (noAttachments && isStraightLine(cableType, connectionTypes)) {
// FIXME            Direction facing = connectionTypes.keySet().iterator().next();
// FIXME
// FIXME            switch (cableType) {
// FIXME                case GLASS:
// FIXME                    this.cableBuilder.addStraightGlassConnection(facing, cableColor, emitter);
// FIXME                    break;
// FIXME                case COVERED:
// FIXME                    this.cableBuilder.addStraightCoveredConnection(facing, cableColor, emitter);
// FIXME                    break;
// FIXME                case SMART:
// FIXME                    this.cableBuilder.addStraightSmartConnection(facing, cableColor,
// FIXME                            renderState.getChannelsOnSide().get(facing), emitter);
// FIXME                    break;
// FIXME                case DENSE_COVERED:
// FIXME                    this.cableBuilder.addStraightDenseCoveredConnection(facing, cableColor, emitter);
// FIXME                    break;
// FIXME                case DENSE_SMART:
// FIXME                    this.cableBuilder.addStraightDenseSmartConnection(facing, cableColor,
// FIXME                            renderState.getChannelsOnSide().get(facing), emitter);
// FIXME                    break;
// FIXME                default:
// FIXME                    break;
// FIXME            }
// FIXME
// FIXME            return null; // Don't render the other form of connection
// FIXME        }
// FIXME
// FIXME        this.cableBuilder.addCableCore(renderState.getCoreType(), cableColor, emitter);
// FIXME
// FIXME        // Render all internal connections to attachments
// FIXME        EnumMap<Direction, Integer> attachmentConnections = renderState.getAttachmentConnections();
// FIXME        for (Direction facing : attachmentConnections.keySet()) {
// FIXME            int distance = attachmentConnections.get(facing);
// FIXME            int channels = renderState.getChannelsOnSide().get(facing);
// FIXME
// FIXME            switch (cableType) {
// FIXME                case GLASS:
// FIXME                    this.cableBuilder.addConstrainedGlassConnection(facing, cableColor, distance, emitter);
// FIXME                    break;
// FIXME                case COVERED:
// FIXME                    this.cableBuilder.addConstrainedCoveredConnection(facing, cableColor, distance, emitter);
// FIXME                    break;
// FIXME                case SMART:
// FIXME                    this.cableBuilder.addConstrainedSmartConnection(facing, cableColor, distance, channels, emitter);
// FIXME                    break;
// FIXME                case DENSE_COVERED:
// FIXME                case DENSE_SMART:
// FIXME                    // Dense cables do not render connections to parts since none can be attached
// FIXME                    break;
// FIXME                default:
// FIXME                    break;
// FIXME            }
// FIXME        }
// FIXME
// FIXME        // Render all outgoing connections using the appropriate type
// FIXME        for (final Entry<Direction, AECableType> connection : connectionTypes.entrySet()) {
// FIXME            final Direction facing = connection.getKey();
// FIXME            final AECableType connectionType = connection.getValue();
// FIXME            final boolean cableBusAdjacent = renderState.getCableBusAdjacent().contains(facing);
// FIXME            final int channels = renderState.getChannelsOnSide().get(facing);
// FIXME
// FIXME            switch (cableType) {
// FIXME                case GLASS:
// FIXME                    this.cableBuilder.addGlassConnection(facing, cableColor, connectionType, cableBusAdjacent,
// FIXME                            emitter);
// FIXME                    break;
// FIXME                case COVERED:
// FIXME                    this.cableBuilder.addCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent,
// FIXME                            emitter);
// FIXME                    break;
// FIXME                case SMART:
// FIXME                    this.cableBuilder.addSmartConnection(facing, cableColor, connectionType, cableBusAdjacent, channels,
// FIXME                            emitter);
// FIXME                    break;
// FIXME                case DENSE_COVERED:
// FIXME                    this.cableBuilder.addDenseCoveredConnection(facing, cableColor, connectionType, cableBusAdjacent,
// FIXME                            emitter);
// FIXME                    break;
// FIXME                case DENSE_SMART:
// FIXME                    this.cableBuilder.addDenseSmartConnection(facing, cableColor, connectionType, cableBusAdjacent,
// FIXME                            channels, emitter);
// FIXME                    break;
// FIXME                default:
// FIXME                    break;
// FIXME            }
// FIXME        }

        return builder.build();
    }

    /**
     * Gets a list of texture sprites appropriate for particles (digging, etc.)
     * given the render state for a cable bus.
     */
    public List<Sprite> getParticleTextures(CableBusRenderState renderState) {
        CableCoreType coreType = CableCoreType.fromCableType(renderState.getCableType());
        AEColor cableColor = renderState.getCableColor();

        List<Sprite> result = new ArrayList<>();

        if (coreType != null) {
            result.add(this.cableBuilder.getCoreTexture(coreType, cableColor));
        }

        // If no core is present, just use the first part that comes into play
        for (Direction side : renderState.getAttachments().keySet()) {
            IPartModel partModel = renderState.getAttachments().get(side);

            for (Identifier model : partModel.getModels()) {
                BakedModel bakedModel = this.partModels.get(model);

                if (bakedModel == null) {
                    throw new IllegalStateException("Trying to use an unregistered part model: " + model);
                }

                Sprite particleTexture = bakedModel.getSprite();

                // If a part sub-model has no particle texture (indicated by it being the
                // missing texture),
                // don't add it, so we don't get ugly missing texture break particles.
                if (!isMissingTexture(particleTexture)) {
                    result.add(particleTexture);
                }
            }
        }

        return result;
    }

    private boolean isMissingTexture(Sprite particleTexture) {
        return particleTexture instanceof MissingSprite;
    }


    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean hasDepth() {
        return false;
    }

    @Override
    public boolean isSideLit() {
        return false;// TODO
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public Sprite getSprite() {
        return this.particleTexture;
    }

    @Override
    public ModelTransformation getTransformation() {
        return ModelTransformation.NONE;
    }

    @Override
    public ModelOverrideList getOverrides() {
        return ModelOverrideList.EMPTY;
    }

    public static void clearCache() {
        CABLE_MODEL_CACHE.clear();
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
        throw new IllegalStateException();
    }

}