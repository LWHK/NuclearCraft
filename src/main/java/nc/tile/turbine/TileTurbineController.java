package nc.tile.turbine;

import nc.handler.TileInfoHandler;
import nc.multiblock.cuboidal.CuboidalPartPositionType;
import nc.multiblock.turbine.*;
import nc.tile.TileContainerInfo;
import nc.util.NCMath;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.*;

import static nc.block.property.BlockProperties.FACING_ALL;

public class TileTurbineController extends TileTurbinePart implements ITurbineController<TileTurbineController> {
	
	protected final TileContainerInfo<TileTurbineController> info = TileInfoHandler.getTileContainerInfo("turbine_controller");
	
	protected boolean isRenderer = false;
	
	public TileTurbineController() {
		super(CuboidalPartPositionType.WALL);
	}
	
	@Override
	public String getLogicID() {
		return "turbine";
	}
	
	@Override
	public TileContainerInfo<TileTurbineController> getContainerInfo() {
		return info;
	}
	
	@Override
	public void onMachineAssembled(Turbine multiblock) {
		doStandardNullControllerResponse(multiblock);
		super.onMachineAssembled(multiblock);
		if (!world.isRemote) {
			EnumFacing facing = getPartPosition().getFacing();
			if (facing != null) {
				world.setBlockState(pos, world.getBlockState(pos).withProperty(FACING_ALL, facing), 2);
			}
		}
	}
	
	@Override
	public int[] weakSidesToCheck(World worldIn, BlockPos posIn) {
		return new int[] {2, 3, 4, 5};
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		if (!isRenderer || !isMultiblockAssembled()) {
			return Block.FULL_BLOCK_AABB.offset(pos);
		}
		return new AxisAlignedBB(getMultiblock().getMinimumCoord(), getMultiblock().getMaximumCoord());
	}
	
	@Override
	public double getDistanceSq(double x, double y, double z) {
		double dX, dY, dZ;
		if (!isRenderer || !isMultiblockAssembled()) {
			dX = pos.getX() + 0.5D - x;
			dY = pos.getY() + 0.5D - y;
			dZ = pos.getZ() + 0.5D - z;
		}
		else {
			dX = getMultiblock().getMiddleX() + 0.5D - x;
			dY = getMultiblock().getMiddleY() + 0.5D - y;
			dZ = getMultiblock().getMiddleZ() + 0.5D - z;
		}
		return dX * dX + dY * dY + dZ * dZ;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		double defaultDistSq = super.getMaxRenderDistanceSquared();
		if (!isRenderer || !isMultiblockAssembled()) {
			return defaultDistSq;
		}
		return defaultDistSq + (NCMath.sq(getMultiblock().getExteriorLengthX()) + NCMath.sq(getMultiblock().getExteriorLengthY()) + NCMath.sq(getMultiblock().getExteriorLengthZ())) / 4D;
	}
	
	@Override
	public void onBlockNeighborChanged(IBlockState state, World worldIn, BlockPos posIn, BlockPos fromPos) {
		super.onBlockNeighborChanged(state, worldIn, posIn, fromPos);
		TurbineLogic logic = getLogic();
		if (logic != null) {
			logic.setIsTurbineOn();
		}
	}
	
	@Override
	public boolean isRenderer() {
		return isRenderer;
	}
	
	@Override
	public void setIsRenderer(boolean isRenderer) {
		this.isRenderer = isRenderer;
	}
}
