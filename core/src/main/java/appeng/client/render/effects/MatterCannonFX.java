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

package appeng.client.render.effects;

import net.fabricmc.api.EnvType;
import net.minecraft.client.particle.*;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.world.World;
import net.fabricmc.api.Environment;

import appeng.api.util.AEPartLocation;

public class MatterCannonFX extends SpriteBillboardParticle {

    public MatterCannonFX(final World par1World, final double x, final double y, final double z,
            IAnimatedSprite sprite) {
        super(par1World, x, y, z);
        this.particleGravity = 0;
        this.particleBlue = 1;
        this.particleGreen = 1;
        this.particleRed = 1;
        this.particleAlpha = 1.4f;
        this.particleScale = 1.1f;
        this.motionX = 0.0f;
        this.motionY = 0.0f;
        this.motionZ = 0.0f;
        this.selectSpriteRandomly(sprite);
    }

    public void fromItem(final AEPartLocation d) {
        this.particleScale *= 1.2f;
    }

    @Override
    public IParticleRenderType getRenderType() {
        return IParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void tick() {
        this.prevX = this.posX;
        this.prevY = this.posY;
        this.prevZ = this.posZ;

        if (this.age++ >= this.maxAge) {
            this.setExpired();
        }

        this.motionY -= 0.04D * this.particleGravity;
        this.move(this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.9800000190734863D;
        this.motionY *= 0.9800000190734863D;
        this.motionZ *= 0.9800000190734863D;

        this.particleScale *= 1.19f;
        this.particleAlpha *= 0.59f;
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements IParticleFactory<DefaultParticleType> {
        private final IAnimatedSprite spriteSet;

        public Factory(IAnimatedSprite spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle makeParticle(DefaultParticleType data, World world, double x, double y, double z, double xSpeed,
                                     double ySpeed, double zSpeed) {
            return new MatterCannonFX(world, x, y, z, spriteSet);
        }
    }

}