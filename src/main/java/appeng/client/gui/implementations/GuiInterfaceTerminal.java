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

package appeng.client.gui.implementations;


import java.io.IOException;
import java.util.*;

import appeng.util.BlockPosUtils;
import com.google.common.collect.HashMultimap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.core.localization.GuiText;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.util.Platform;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;


import static appeng.client.render.BlockPosHighlighter.hilightBlock;


public class GuiInterfaceTerminal extends AEBaseGui
{

	private static final int LINES_ON_PAGE = 6;

	// TODO: copied from GuiMEMonitorable. It looks not changed, maybe unneeded?
	private final int offsetX = 9;

	private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
	private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
	private final HashMap<ClientDCInternalInv,BlockPos> blockPosHashMap = new HashMap<>();
	private final HashMap<GuiButton,ClientDCInternalInv> guiButtonHashMap = new HashMap<>();
	private final ArrayList<String> names = new ArrayList<>();
	private final ArrayList<Object> lines = new ArrayList<>();

	private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();

	private boolean refreshList = false;
	private MEGuiTextField searchFieldOutputs;
	private MEGuiTextField searchFieldInputs;
	private PartInterfaceTerminal partInterfaceTerminal;
	private GuiButton guiButtonHide;
	private GuiButton guiButtonNextAssembler;

	public GuiInterfaceTerminal( final InventoryPlayer inventoryPlayer, final PartInterfaceTerminal te )
	{
		super( new ContainerInterfaceTerminal( inventoryPlayer, te ) );

		this.partInterfaceTerminal = te;
		final GuiScrollbar scrollbar = new GuiScrollbar();
		this.setScrollBar( scrollbar );
		this.xSize = 195;
		this.ySize = 236;
	}

	@Override
	public void initGui()
	{
		super.initGui();

		this.getScrollBar().setLeft( 175 );
		this.getScrollBar().setHeight( 106 );
		this.getScrollBar().setTop( 32 );

		this.searchFieldInputs = new MEGuiTextField( this.fontRenderer, this.guiLeft + Math.max( 16, this.offsetX ), this.guiTop + 18, 65, 12 );
		this.searchFieldInputs.setEnableBackgroundDrawing( false );
		this.searchFieldInputs.setMaxStringLength( 25 );
		this.searchFieldInputs.setTextColor( 0xFFFFFF );
		this.searchFieldInputs.setVisible( true );
		this.searchFieldInputs.setFocused( false );

//		this.searchFieldOutputs = new MEGuiTextField( this.fontRenderer, this.guiLeft + Math.max( 105, this.offsetX ), this.guiTop + 4, 65, 12 );
		this.searchFieldOutputs = new MEGuiTextField( this.fontRenderer, this.guiLeft + Math.max( 94, this.offsetX ), this.guiTop + 18, 65, 12 );
		this.searchFieldOutputs.setEnableBackgroundDrawing( false );
		this.searchFieldOutputs.setMaxStringLength( 25 );
		this.searchFieldOutputs.setTextColor( 0xFFFFFF );
		this.searchFieldOutputs.setVisible( true );
		this.searchFieldOutputs.setFocused( true );

		this.searchFieldInputs.setText( partInterfaceTerminal.in );
		this.searchFieldOutputs.setText( partInterfaceTerminal.out );
	}

	@Override
	public void onGuiClosed()
	{
		partInterfaceTerminal.saveSearchStrings( this.searchFieldInputs.getText().toLowerCase(), this.searchFieldOutputs.getText().toLowerCase() );
		super.onGuiClosed();
	}

	@Override
	public void drawFG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
	{
		this.buttonList.clear();

		this.fontRenderer.drawString( this.getGuiDisplayName( GuiText.InterfaceTerminal.getLocal() ), 8, 6, 4210752 );
		this.fontRenderer.drawString( GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752 );

		final int ex = this.getScrollBar().getCurrentScroll();

		this.guiButtonNextAssembler = new GuiButton( -1, guiLeft + 110, guiTop + 4, 12, 12, "M" );
		this.buttonList.add( guiButtonNextAssembler );

		this.guiButtonHide = new GuiButton( -1, guiLeft + 128, guiTop + 4, 60, 12, "Hide Full" );
		this.buttonList.add( guiButtonHide );

		final Iterator<Slot> o = this.inventorySlots.inventorySlots.iterator();
		while( o.hasNext() )
		{
			if( o.next() instanceof SlotDisconnected )
			{
				o.remove();
			}
		}

		int offset = 32;
		for( int x = 0; x < LINES_ON_PAGE && ex + x < this.lines.size(); x++ )
		{
			final Object lineObj = this.lines.get( ex + x );
			if( lineObj instanceof ClientDCInternalInv )
			{
				final ClientDCInternalInv inv = (ClientDCInternalInv) lineObj;
				for( int z = 0; z < inv.getInventory().getSlots(); z++ )
				{
					this.inventorySlots.inventorySlots.add( new SlotDisconnected( inv, z, z * 18 + 8, 1 + offset ) );
				}
				GuiButton guiButton = new GuiButton( x, guiLeft + 1, guiTop + offset + 3, 8, 10, "?" );
				guiButtonHashMap.put( guiButton , inv);
				this.buttonList.add( guiButton );

			}
			else if( lineObj instanceof String )
			{
				String name = (String) lineObj;
				final int rows = this.byName.get( name ).size();
				if( rows > 1 )
				{
					name = name + " (" + rows + ')';
				}

				while( name.length() > 2 && this.fontRenderer.getStringWidth( name ) > 155 )
				{
					name = name.substring( 0, name.length() - 1 );
				}

				this.fontRenderer.drawString( name, 10, 6 + offset, 4210752 );
			}
			offset += 18;
		}
	}

	@Override
	protected void mouseClicked( final int xCoord, final int yCoord, final int btn ) throws IOException
	{
		this.searchFieldInputs.mouseClicked( xCoord, yCoord, btn );

		if( btn == 1 && this.searchFieldInputs.isMouseIn( xCoord, yCoord ) )
		{
			this.searchFieldInputs.setText( "" );
			this.refreshList();
		}

		this.searchFieldOutputs.mouseClicked( xCoord, yCoord, btn );

		if( btn == 1 && this.searchFieldOutputs.isMouseIn( xCoord, yCoord ) )
		{
			this.searchFieldOutputs.setText( "" );
			this.refreshList();
		}

		super.mouseClicked( xCoord, yCoord, btn );
	}

	@Override
	protected void actionPerformed( final GuiButton btn ) throws IOException
	{
		if( guiButtonHashMap.containsKey( btn ) )
		{
			BlockPos blockPos = blockPosHashMap.get( guiButtonHashMap.get( this.selectedButton ) );
			BlockPos blockPos2 = mc.player.getPosition();
			hilightBlock( blockPos, System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2) );
			mc.player.sendStatusMessage( new TextComponentString( "The interface is now highlighted at " + "X: " + blockPos.getX() + "Y: " + blockPos.getY() + "Z: " + blockPos.getZ() ), false );
			mc.player.closeScreen();
		}

		if (btn == guiButtonHide)
		{
			partInterfaceTerminal.onlyInterfacesWithFreeSlots = !partInterfaceTerminal.onlyInterfacesWithFreeSlots;
			this.refreshList();
		}

		if (btn == guiButtonNextAssembler)
		{
			// Set Search to "Molecular Assembler" and set "Only Free Interface"
			boolean currentOnlyInterfacesWithFreeSlots = this.partInterfaceTerminal.onlyInterfacesWithFreeSlots;
			String currentSearchText = this.searchFieldOutputs.getText();

			this.partInterfaceTerminal.onlyInterfacesWithFreeSlots = true;
			this.searchFieldOutputs.setText("Molecular Assembler");

			this.refreshList();

			this.partInterfaceTerminal.onlyInterfacesWithFreeSlots = currentOnlyInterfacesWithFreeSlots;
			this.searchFieldOutputs.setText(currentSearchText);
		}
	}

	@Override
	public void drawBG( final int offsetX, final int offsetY, final int mouseX, final int mouseY )
	{
		this.bindTexture( "guis/newinterfaceterminal.png" );
		this.drawTexturedModalRect( offsetX, offsetY, 0, 0, this.xSize, this.ySize );

		int offset = 32;
		final int ex = this.getScrollBar().getCurrentScroll();

		for( int x = 0; x < LINES_ON_PAGE && ex + x < this.lines.size(); x++ )
		{
			final Object lineObj = this.lines.get( ex + x );
			if( lineObj instanceof ClientDCInternalInv )
			{
				final ClientDCInternalInv inv = (ClientDCInternalInv) lineObj;

				GlStateManager.color( 1, 1, 1, 1 );
				final int width = inv.getInventory().getSlots() * 18;
				this.drawTexturedModalRect( offsetX + 7, offsetY + offset, 7, 153, width, 18 );
			}
			offset += 18;
		}

		if( this.searchFieldInputs != null )
		{
			this.searchFieldInputs.drawTextBox();
		}

		if( this.searchFieldOutputs != null )
		{
			this.searchFieldOutputs.drawTextBox();
		}
	}

	@Override
	protected void keyTyped( final char character, final int key ) throws IOException
	{
		if( !this.checkHotbarKeys( key ) )
		{
			if( character == ' ' && this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused() )
			{
				return;
			}

			if( character == ' ' && this.searchFieldOutputs.getText().isEmpty() && this.searchFieldOutputs.isFocused())
			{
				return;
			}

			if( this.searchFieldInputs.textboxKeyTyped( character, key ) || this.searchFieldOutputs.textboxKeyTyped( character, key ))
			{
				this.refreshList();
			}

			else
			{
				super.keyTyped( character, key );
			}
		}
	}

	public void postUpdate( final NBTTagCompound in )
	{
		if( in.getBoolean( "clear" ) )
		{
			this.byId.clear();
			this.refreshList = true;
		}

		for( final Object oKey : in.getKeySet() )
		{
			final String key = (String) oKey;
			if( key.startsWith( "=" ) )
			{
				try
				{
					final long id = Long.parseLong( key.substring( 1 ), Character.MAX_RADIX );
					final NBTTagCompound invData = in.getCompoundTag( key );
					final ClientDCInternalInv current = this.getById( id, invData.getLong( "sortBy" ), invData.getString( "un" ) );
					blockPosHashMap.put( current, NBTUtil.getPosFromTag( invData.getCompoundTag( "pos" )) );

					for( int x = 0; x < current.getInventory().getSlots(); x++ )
					{
						final String which = Integer.toString( x );
						if( invData.hasKey( which ) )
						{
							current.getInventory().setStackInSlot( x, new ItemStack( invData.getCompoundTag( which ) ) );
						}
					}
				}
				catch( final NumberFormatException ignored )
				{
				}
			}
		}

		if( this.refreshList )
		{
			this.refreshList = false;
			// invalid caches on refresh
			this.cachedSearches.clear();
			this.refreshList();
		}
	}

	/**
	 * Rebuilds the list of interfaces.
	 *
	 * Respects a search term if present (ignores case) and adding only matching patterns.
	 */
	private void refreshList()
	{
		this.byName.clear();
		this.buttonList.clear();

		final String searchFieldInputs = this.searchFieldInputs.getText().toLowerCase();
		final String searchFieldOutputs = this.searchFieldOutputs.getText().toLowerCase();

		final Set<Object> cachedSearch = this.getCacheForSearchTerm( "IN:" + searchFieldInputs + " OUT:" + searchFieldOutputs );
		final boolean rebuild = cachedSearch.isEmpty();

		for( final ClientDCInternalInv entry : this.byId.values() )
		{
			// ignore inventory if not doing a full rebuild and cache already marks it as miss.
			if( !rebuild && !cachedSearch.contains( entry ) )
			{
				continue;
			}

			// Shortcut to skip any filter if search term is ""/empty
			boolean found = (searchFieldInputs.isEmpty() && searchFieldOutputs.isEmpty());

			// Search if the current inventory holds a pattern containing the search term.
			if( !found )
			{
				for( final ItemStack itemStack : entry.getInventory() )
				{
					if( !searchFieldInputs.isEmpty() && !searchFieldOutputs.isEmpty() )
						found = ( this.itemStackMatchesSearchTerm( itemStack, searchFieldInputs, 0 ) || this.itemStackMatchesSearchTerm( itemStack, searchFieldOutputs, 1 ) );
					else if( !searchFieldInputs.isEmpty() )
						found = ( this.itemStackMatchesSearchTerm( itemStack, searchFieldInputs, 0 ) );
					else if( !searchFieldOutputs.isEmpty() )
						found = ( this.itemStackMatchesSearchTerm( itemStack, searchFieldOutputs, 1 ) );
					if( found )
					{
						break;
					}
				}
			} 

			// If only Interfaces with empty slots should be shown, check that here
			boolean interfaceHasFreeSlots = false;
			if (partInterfaceTerminal.onlyInterfacesWithFreeSlots) {
				for( final ItemStack itemStack : entry.getInventory() )
				{
					if(itemStack.isEmpty()){
						interfaceHasFreeSlots = true;
						break;
					}
				}
			}

			// if found, filter skipped or machine name matching the search term, add it
			if( found || (entry.getName().toLowerCase().contains( searchFieldInputs ) && entry.getName().toLowerCase().contains( searchFieldOutputs )))
			{
				if (!partInterfaceTerminal.onlyInterfacesWithFreeSlots)
				{
					this.byName.put( entry.getName(), entry );
					cachedSearch.add( entry );
				}
				else if ( interfaceHasFreeSlots ){
					this.byName.put( entry.getName(), entry );
					cachedSearch.add( entry );
				}
			}
			else
			{
				cachedSearch.remove( entry );
			}
		}

		this.names.clear();
		this.names.addAll( this.byName.keySet() );

		Collections.sort( this.names );

		this.lines.clear();
		this.lines.ensureCapacity( this.getMaxRows() );

		for( final String n : this.names )
		{
			this.lines.add( n );

			final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>();
			clientInventories.addAll( this.byName.get( n ) );

			Collections.sort( clientInventories );
			this.lines.addAll( clientInventories );
		}

		this.getScrollBar().setRange( 0, this.lines.size() - LINES_ON_PAGE, 2 );
	}

	private boolean itemStackMatchesSearchTerm( final ItemStack itemStack, final String searchTerm,int pass )
	{
		if( itemStack.isEmpty() )
		{
			return false;
		}

		final NBTTagCompound encodedValue = itemStack.getTagCompound();

		if( encodedValue == null )
		{
			return false;
		}

		NBTTagList tag = new NBTTagList();

		if (pass == 0)
		{
			tag = encodedValue.getTagList( "in", 10 );
		}
		else
		{
			tag = encodedValue.getTagList( "out", 10 );
		}

		for( int i = 0; i < tag.tagCount(); i++ )
		{

			final ItemStack parsedItemStack = new ItemStack( tag.getCompoundTagAt( i ) );
			if( !parsedItemStack.isEmpty() )
			{
				final String displayName = Platform
						.getItemDisplayName( AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class ).createStack( parsedItemStack ) )
						.toLowerCase();
				if( displayName.contains( searchTerm ) )
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Tries to retrieve a cache for a with search term as keyword.
	 *
	 * If this cache should be empty, it will populate it with an earlier cache if available or at least the cache for
	 * the empty string.
	 *
	 * @param searchTerm the corresponding search
	 *
	 * @return a Set matching a superset of the search term
	 */
	private Set<Object> getCacheForSearchTerm( final String searchTerm )
	{
		if( !this.cachedSearches.containsKey( searchTerm ) )
		{
			this.cachedSearches.put( searchTerm, new HashSet<>() );
		}

		final Set<Object> cache = this.cachedSearches.get( searchTerm );

		if( cache.isEmpty() && searchTerm.length() > 1 )
		{
			cache.addAll( this.getCacheForSearchTerm( searchTerm.substring( 0, searchTerm.length() - 1 ) ) );
			return cache;
		}

		return cache;
	}

	/**
	 * The max amount of unique names and each inv row. Not affected by the filtering.
	 *
	 * @return max amount of unique names and each inv row
	 */
	private int getMaxRows()
	{
		return this.names.size() + this.byId.size();
	}

	private ClientDCInternalInv getById( final long id, final long sortBy, final String string )
	{
		ClientDCInternalInv o = this.byId.get( id );

		if( o == null )
		{
			this.byId.put( id, o = new ClientDCInternalInv( 9, id, sortBy, string ) );
			this.refreshList = true;
		}

		return o;
	}
}
