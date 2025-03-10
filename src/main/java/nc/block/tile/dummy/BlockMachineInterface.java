package nc.block.tile.dummy;

import nc.tile.ITileGui;
import nc.tile.dummy.TileMachineInterface;
import nc.tile.fluid.ITileFluid;
import nc.tile.processor.IProcessor;
import nc.util.BlockHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockMachineInterface extends BlockSimpleDummy<TileMachineInterface> {
	
	public BlockMachineInterface(String name) {
		super(name);
	}
	
	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		if (hand != EnumHand.MAIN_HAND || player.isSneaking()) {
			return false;
		}
		if (world.isRemote) {
			return true;
		}
		
		TileEntity tile = world.getTileEntity(pos);
		if (tile instanceof TileMachineInterface iface) {
			if (iface.masterPosition == null) {
				iface.findMaster();
			}
			if (iface.masterPosition == null) {
				return false;
			}
			BlockPos masterPos = iface.masterPosition;
			TileEntity master = world.getTileEntity(masterPos);
			if (master instanceof ITileFluid tileFluid) {
				boolean accessedTanks = BlockHelper.accessTanks(player, hand, facing, tileFluid);
				if (accessedTanks) {
					if (master instanceof IProcessor<?, ?, ?> processor) {
						processor.refreshRecipe();
						processor.refreshActivity();
					}
					return true;
				}
			}
			if (master instanceof ITileGui) {
				((ITileGui<?, ?, ?>) master).openGui(world, masterPos, player);
			}
		}
		return true;
	}
}
