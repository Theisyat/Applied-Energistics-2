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

package appeng.tile;

import java.util.List;

import javax.annotation.Nonnull;

import alexiil.mc.lib.attributes.item.FixedItemInv;
import alexiil.mc.lib.attributes.item.FixedItemInv;
import alexiil.mc.lib.attributes.item.impl.EmptyFixedItemInv;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;

import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

public abstract class AEBaseInvBlockEntity extends AEBaseBlockEntity implements IAEAppEngInventory {

    public AEBaseInvBlockEntity(BlockEntityType<?> tileEntityTypeIn) {
        super(tileEntityTypeIn);
    }

    @Override
    public void fromTag(BlockState state, final CompoundTag data) {
        super.fromTag(state, data);
        final FixedItemInv inv = this.getInternalInventory();
        if (inv != EmptyFixedItemInv.INSTANCE) {
            final CompoundTag opt = data.getCompound("inv");
            for (int x = 0; x < inv.getSlotCount(); x++) {
                final CompoundTag item = opt.getCompound("item" + x);
                ItemHandlerUtil.setStackInSlot(inv, x, ItemStack.fromTag(item));
            }
        }
    }

    public abstract @Nonnull
    FixedItemInv getInternalInventory();

    @Override
    public CompoundTag toTag(final CompoundTag data) {
        super.toTag(data);
        final FixedItemInv inv = this.getInternalInventory();
        if (inv != EmptyFixedItemInv.INSTANCE) {
            final CompoundTag opt = new CompoundTag();
            for (int x = 0; x < inv.getSlotCount(); x++) {
                final CompoundTag item = new CompoundTag();
                final ItemStack is = inv.getInvStack(x);
                if (!is.isEmpty()) {
                    is.toTag(item);
                }
                opt.put("item" + x, item);
            }
            data.put("inv", opt);
        }
        return data;
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        final FixedItemInv inv = this.getInternalInventory();

        for (int l = 0; l < inv.getSlotCount(); l++) {
            final ItemStack is = inv.getInvStack(l);
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public abstract void onChangeInventory(FixedItemInv inv, int slot, InvOperation mc, ItemStack removed,
                                           ItemStack added);

    protected @Nonnull
    FixedItemInv getItemHandlerForSide(@Nonnull Direction side) {
        return this.getInternalInventory();
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == null) {
                return (LazyOptional<T>) LazyOptional.of(this::getInternalInventory);
            } else {
                return (LazyOptional<T>) LazyOptional.of(() -> getItemHandlerForSide(facing));
            }
        }
        return super.getCapability(capability, facing);
    }

}