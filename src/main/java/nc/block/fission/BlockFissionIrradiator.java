package nc.block.fission;

import nc.multiblock.fission.FissionReactor;
import nc.tile.fission.TileFissionIrradiator;
import nc.util.Lang;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.*;
import net.minecraft.world.World;

public class BlockFissionIrradiator extends BlockFissionPart {
	
	public BlockFissionIrradiator() {
		super();
	}
	
	@Override
	public TileEntity createNewTileEntity(World world, int metadata) {
		return new TileFissionIrradiator();
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (hand != EnumHand.MAIN_HAND || player.isSneaking()) {
			return false;
		}
		
		if (!world.isRemote) {
			TileEntity tile = world.getTileEntity(pos);
			if (tile instanceof TileFissionIrradiator irradiator) {
				FissionReactor reactor = irradiator.getMultiblock();
				if (reactor != null) {
					ItemStack heldStack = player.getHeldItem(hand);
					if (irradiator.canModifyFilter(0) && irradiator.getInventoryStacks().get(0).isEmpty() && !heldStack.isItemEqual(irradiator.getFilterStacks().get(0)) && irradiator.isItemValidForSlotInternal(0, heldStack)) {
						player.sendMessage(new TextComponentString(Lang.localize("message.nuclearcraft.filter") + " " + TextFormatting.BOLD + heldStack.getDisplayName()));
						ItemStack filter = heldStack.copy();
						filter.setCount(1);
						irradiator.getFilterStacks().set(0, filter);
						irradiator.onFilterChanged(0);
					}
					else {
						irradiator.openGui(world, pos, player);
					}
					return true;
				}
			}
		}
		return rightClickOnPart(world, pos, player, hand, facing, true);
	}
	
	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		if (!keepInventory) {
			TileEntity tile = world.getTileEntity(pos);
			if (tile instanceof TileFissionIrradiator irradiator) {
				dropItems(world, pos, irradiator.getInventoryStacksInternal());
				// world.updateComparatorOutputLevel(pos, this);
				// FissionReactor reactor = irradiator.getMultiblock();
				// world.removeTileEntity(pos);
				/*if (reactor != null) {
					reactor.getLogic().refreshPorts();
				}*/
			}
		}
		// super.breakBlock(world, pos, state);
		world.removeTileEntity(pos);
	}
}
