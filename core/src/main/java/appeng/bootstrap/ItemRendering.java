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

package appeng.bootstrap;

import net.fabricmc.api.Environment;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.item.Item;
import net.fabricmc.api.EnvType;

import appeng.bootstrap.components.ItemColorComponent;

class ItemRendering implements IItemRendering {

    @Environment(EnvType.CLIENT)
    private ItemColorProvider itemColor;

    @Override
    @Environment(EnvType.CLIENT)
    public IItemRendering color(ItemColorProvider itemColor) {
        this.itemColor = itemColor;
        return this;
    }

    void apply(FeatureFactory factory, Item item) {
        if (this.itemColor != null) {
            factory.addBootstrapComponent(new ItemColorComponent(item, this.itemColor));
        }
    }

}