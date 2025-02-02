package nc.multiblock.machine;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.*;
import nc.config.NCConfig;
import nc.handler.SoundHandler;
import nc.init.NCSounds;
import nc.network.multiblock.*;
import nc.recipe.*;
import nc.recipe.multiblock.ElectrolyzerElectrolyteRecipeHandler;
import nc.tile.internal.fluid.Tank.TankInfo;
import nc.tile.machine.*;
import nc.tile.multiblock.TilePartAbstract.SyncReason;
import nc.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.*;

import static nc.config.NCConfig.*;

public class ElectrolyzerLogic extends MachineLogic {
	
	protected ElectrolyzerElectrolyteRecipeHandler electrolyteRecipeHandler = null;
	public double electrolyteEfficiency = 0D;
	
	protected double prevSpeedMultiplier = 0D;
	
	public ElectrolyzerLogic(Machine machine) {
		super(machine);
	}
	
	public ElectrolyzerLogic(MachineLogic oldLogic) {
		super(oldLogic);
		if (oldLogic instanceof ElectrolyzerLogic oldElectrolyzerLogic) {
			prevSpeedMultiplier = oldElectrolyzerLogic.prevSpeedMultiplier;
		}
	}
	
	@Override
	public String getID() {
		return "electrolyzer";
	}
	
	@Override
	public BasicRecipeHandler getRecipeHandler() {
		return NCRecipes.multiblock_electrolyzer;
	}
	
	@Override
	public double defaultProcessTime() {
		return NCConfig.machine_electrolyzer_time;
	}
	
	@Override
	public double defaultProcessPower() {
		return NCConfig.machine_electrolyzer_power;
	}
	
	protected <T extends TileMachinePart> boolean checkElectrodeTerminalPairs(Long2ObjectMap<T> electrodeMap, String type) {
		int minY = multiblock.getMinY(), maxY = multiblock.getMaxY();
		for (Long2ObjectMap.Entry<T> entry : electrodeMap.long2ObjectEntrySet()) {
			BlockPos pos = BlockPos.fromLong(entry.getLongKey());
			int x = pos.getX(), y = pos.getY(), z = pos.getZ();
			if ((y != minY || !electrodeMap.containsKey(new BlockPos(x, maxY, z).toLong())) && (y != maxY || !electrodeMap.containsKey(new BlockPos(x, minY, z).toLong()))) {
				if (multiblock.getLastError() == null) {
					multiblock.setLastError("nuclearcraft.multiblock_validation.electrolyzer.invalid_" + type, pos, pos.getX(), pos.getY(), pos.getZ());
				}
				return false;
			}
		}
		return true;
	}
	
	@Override
	public void onMachineBroken() {
		super.onMachineBroken();
		
		if (getWorld().isRemote) {
			clearSounds();
		}
	}
	
	@Override
	public boolean isMachineWhole() {
		if (!super.isMachineWhole() || !multiblock.hasAxialSymmetry(Axis.Y)) {
			return false;
		}
		
		Long2ObjectMap<TileElectrolyzerCathodeTerminal> cathodeMap = getPartMap(TileElectrolyzerCathodeTerminal.class);
		Long2ObjectMap<TileElectrolyzerAnodeTerminal> anodeMap = getPartMap(TileElectrolyzerAnodeTerminal.class);
		
		if (!checkElectrodeTerminalPairs(cathodeMap, "cathode") || !checkElectrodeTerminalPairs(anodeMap, "anode")) {
			return false;
		}
		
		BlockPos corner = multiblock.getExtremeInteriorCoord(false, false, false);
		int interiorX = multiblock.getInteriorLengthX(), interiorZ = multiblock.getInteriorLengthZ();
		int minY = multiblock.getMinY(), maxY = multiblock.getMaxY();
		
		IBlockState[][] plane = new IBlockState[interiorX][];
		
		for (int i = 0; i < interiorX; ++i) {
			IBlockState[] row = new IBlockState[interiorZ];
			for (int j = 0; j < interiorZ; ++j) {
				row[j] = getWorld().getBlockState(corner.add(i, 0, j));
			}
			plane[i] = row;
		}
		
		boolean[][] visited = new boolean[interiorX][];
		for (int i = 0; i < interiorX; ++i) {
			visited[i] = new boolean[interiorZ];
		}
		
		ObjectSet<ElectrolyzerRegion> regions = new ObjectOpenHashSet<>();
		
		ObjectSet<Vec2i> allElectrodes = new ObjectOpenHashSet<>();
		Object2DoubleMap<Vec2i> allDiaphragms = new Object2DoubleOpenHashMap<>();
		
		for (int i = 0; i < interiorX; ++i) {
			for (int j = 0; j < interiorZ; ++j) {
				if (visited[i][j]) {
					continue;
				}
				
				Deque<Vec2i> vecs = new ArrayDeque<>();
				
				Consumer<Vec2i> push = x -> {
					if (!visited[x.u][x.v]) {
						vecs.push(x);
						visited[x.u][x.v] = true;
					}
				};
				
				push.accept(new Vec2i(i, j));
				
				ElectrolyzerRegion region = new ElectrolyzerRegion();
				stackLoop: while (!vecs.isEmpty()) {
					Vec2i next = vecs.pop();
					
					for (Vec2i dir : Vec2i.DIRS_WITH_ZERO) {
						Vec2i vec = next.add(dir);
						if (vec.u >= 0 && vec.u < interiorX && vec.v >= 0 && vec.v < interiorZ) {
							IBlockState blockState = plane[vec.u][vec.v];
							if (MaterialHelper.isEmpty(blockState.getMaterial())) {
								push.accept(vec);
								continue;
							}
							
							long minPosLongXZ = corner.add(vec.u, -1, vec.v).toLong();
							BasicRecipe blockRecipe;
							if (cathodeMap.containsKey(minPosLongXZ)) {
								if ((blockRecipe = RecipeHelper.blockRecipe(NCRecipes.electrolyzer_cathode, blockState)) != null) {
									region.cathodes.put(vec, blockRecipe.getElectrolyzerElectrodeEfficiency());
									allElectrodes.add(vec);
									push.accept(vec);
									continue;
								}
							}
							else if (anodeMap.containsKey(minPosLongXZ)) {
								if ((blockRecipe = RecipeHelper.blockRecipe(NCRecipes.electrolyzer_anode, blockState)) != null) {
									region.anodes.put(vec, blockRecipe.getElectrolyzerElectrodeEfficiency());
									allElectrodes.add(vec);
									push.accept(vec);
									continue;
								}
							}
							
							if ((blockRecipe = RecipeHelper.blockRecipe(NCRecipes.machine_diaphragm, blockState)) != null) {
								if (dir.equals(Vec2i.ZERO)) {
									continue stackLoop;
								}
								else {
									region.diaphragms.put(vec, blockRecipe.getMachineDiaphragmEfficiency());
									allDiaphragms.put(vec, blockRecipe.getMachineDiaphragmContactFactor());
									continue;
								}
							}
							
							if (multiblock.getLastError() == null) {
								BlockPos pos = corner.add(vec.u, 0, vec.v);
								multiblock.setLastError("zerocore.api.nc.multiblock.validation.invalid_part_for_interior", pos, pos.getX(), pos.getY(), pos.getZ());
							}
							return false;
						}
					}
				}
				
				if (!region.cathodes.isEmpty() || !region.anodes.isEmpty()) {
					regions.add(region);
				}
			}
		}
		
		int interiorY = multiblock.getInteriorLengthY();
		
		for (ElectrolyzerRegion region : regions) {
			boolean hasCathodes = !region.cathodes.isEmpty(), hasAnodes = !region.anodes.isEmpty();
			if (hasCathodes && hasAnodes) {
				if (multiblock.getLastError() == null) {
					Vec2i vec = (hasCathodes ? region.cathodes : region.anodes).keySet().iterator().next();
					BlockPos pos = corner.add(vec.u, interiorY, vec.v);
					multiblock.setLastError("nuclearcraft.multiblock_validation.electrolyzer.short_circuit", pos, pos.getX(), pos.getY(), pos.getZ());
				}
				return false;
			}
			
			double diaphragmEfficiencyMult;
			if (region.diaphragms.isEmpty()) {
				diaphragmEfficiencyMult = 1D;
			}
			else {
				diaphragmEfficiencyMult = 0D;
				for (Object2DoubleMap.Entry<Vec2i> entry : region.diaphragms.object2DoubleEntrySet()) {
					diaphragmEfficiencyMult += entry.getDoubleValue();
				}
				diaphragmEfficiencyMult /= region.diaphragms.size();
			}
			
			region.efficiencyMult = diaphragmEfficiencyMult;
		}
		
		baseSpeedMultiplier = 0D;
		
		for (ElectrolyzerRegion region : regions) {
			double cathodeRegionEfficiency = region.efficiencyMult;
			for (Object2DoubleMap.Entry<Vec2i> cathode : region.cathodes.object2DoubleEntrySet()) {
				Vec2i cathodeVec = cathode.getKey();
				double cathodeEfficiency = cathodeRegionEfficiency * cathode.getDoubleValue();
				for (ElectrolyzerRegion other : regions) {
					double anodeRegionEfficiency = other.efficiencyMult;
					for (Object2DoubleMap.Entry<Vec2i> anode : other.anodes.object2DoubleEntrySet()) {
						Vec2i anodeVec = anode.getKey();
						double anodeEfficiency = anodeRegionEfficiency * anode.getDoubleValue();
						baseSpeedMultiplier += cathodeEfficiency * anodeEfficiency / anodeVec.subtract(cathodeVec).abs();
					}
				}
			}
		}
		
		int cathodeCount = cathodeMap.size(), anodeCount = anodeMap.size();
		baseSpeedMultiplier *= (double) interiorY * (double) Math.min(cathodeCount, anodeCount) / (double) Math.max(cathodeCount, anodeCount);
		
		basePowerMultiplier = 0D;
		
		for (Vec2i vec : allElectrodes) {
			basePowerMultiplier += 1D;
			
			for (Vec2i dir : Vec2i.DIRS) {
				Vec2i offset = vec.add(dir);
				if (allDiaphragms.containsKey(offset)) {
					basePowerMultiplier += allDiaphragms.getDouble(offset);
				}
			}
		}
		
		basePowerMultiplier *= interiorY;
		
		return true;
	}
	
	@Override
	public void onAssimilated(Machine assimilator) {
		super.onAssimilated(assimilator);
		
		if (getWorld().isRemote) {
			clearSounds();
		}
	}
	
	// Server
	
	@Override
	protected void setRecipeStats(@Nullable BasicRecipe recipe) {
		super.setRecipeStats(recipe);
		electrolyteRecipeHandler = recipe == null ? null : recipe.getElectrolyzerElectrolyteRecipeHandler();
	}
	
	protected double getReservoirLevelFraction() {
		return reservoirTanks.get(0).getFluidAmountFraction();
	}
	
	@Override
	protected double getSpeedMultiplier() {
		return baseSpeedMultiplier * electrolyteEfficiency * getReservoirLevelFraction();
	}
	
	@Override
	protected double getPowerMultiplier() {
		return basePowerMultiplier * getReservoirLevelFraction();
	}
	
	@Override
	protected boolean readyToProcess() {
		return super.readyToProcess() && getReservoirLevelFraction() > 0D;
	}
	
	@Override
	public void refreshActivity() {
		super.refreshActivity();
		
		RecipeInfo<BasicRecipe> recipeInfo = electrolyteRecipeHandler == null ? null : electrolyteRecipeHandler.getRecipeInfoFromInputs(Collections.emptyList(), reservoirTanks.subList(0, 1));
		electrolyteEfficiency = recipeInfo == null ? 0D : recipeInfo.recipe.getElectrolyzerElectrolyteEfficiency();
	}
	
	// Client
	
	@Override
	public void onUpdateClient() {
		super.onUpdateClient();
		
		updateSounds();
		// updateParticles();
	}
	
	@SideOnly(Side.CLIENT)
	protected void updateSounds() {
		if (machine_electrolyzer_sound_volume == 0D) {
			clearSounds();
			return;
		}
		
		if (isProcessing && multiblock.isAssembled()) {
			double speedMultiplier = getSpeedMultiplier();
			double ratio = (NCMath.EPSILON + Math.abs(speedMultiplier)) / (NCMath.EPSILON + Math.abs(prevSpeedMultiplier));
			multiblock.refreshSounds |= ratio < 0.8D || ratio > 1.25D || multiblock.soundMap.isEmpty();
			
			if (!multiblock.refreshSounds) {
				return;
			}
			multiblock.refreshSounds = false;
			
			clearSounds();
			
			Long2ObjectMap<TileElectrolyzerCathodeTerminal> cathodeMap = getPartMap(TileElectrolyzerCathodeTerminal.class);
			Long2ObjectMap<TileElectrolyzerAnodeTerminal> anodeMap = getPartMap(TileElectrolyzerAnodeTerminal.class);
			int electrodeCount = cathodeMap.size() + anodeMap.size();
			if (electrodeCount <= 0) {
				return;
			}
			
			float volume = (float) (machine_electrolyzer_sound_volume * Math.log1p(speedMultiplier / (4D * Math.sqrt(1D + multiblock.getInteriorLengthY()) * electrodeCount * electrodeCount)));
			Consumer<BlockPos> addSound = x -> multiblock.soundMap.put(x, SoundHandler.startBlockSound(NCSounds.electrolyzer_run, x, volume, 1F));
			
			for (long posLong : cathodeMap.keySet()) {
				addSound.accept(BlockPos.fromLong(posLong));
			}
			for (long posLong : anodeMap.keySet()) {
				addSound.accept(BlockPos.fromLong(posLong));
			}
			
			prevSpeedMultiplier = speedMultiplier;
		}
		else {
			multiblock.refreshSounds = true;
			clearSounds();
		}
	}
	
	@SideOnly(Side.CLIENT)
	protected void clearSounds() {
		multiblock.soundMap.forEach((k, v) -> SoundHandler.stopBlockSound(k));
		multiblock.soundMap.clear();
	}
	
	@SideOnly(Side.CLIENT)
	protected void updateParticles() {
		if (isProcessing && multiblock.isAssembled() && !Minecraft.getMinecraft().isGamePaused()) {
			int minY = multiblock.getMinY(), interiorY = multiblock.getInteriorLengthY();
			for (TileElectrolyzerCathodeTerminal cathode : getParts(TileElectrolyzerCathodeTerminal.class)) {
				BlockPos pos = cathode.getPos();
				if (pos.getY() == minY) {
					spawnElectrodeParticles(pos.up(), interiorY);
				}
			}
			for (TileElectrolyzerAnodeTerminal anode : getParts(TileElectrolyzerAnodeTerminal.class)) {
				BlockPos pos = anode.getPos();
				if (pos.getY() == minY) {
					spawnElectrodeParticles(pos.up(), interiorY);
				}
			}
		}
	}
	
	@SideOnly(Side.CLIENT)
	protected void spawnElectrodeParticles(BlockPos pos, int height) {
		double centerX = pos.getX() + 0.5D, minCenterY = pos.getY() + 0.5D, centerZ = pos.getZ() + 0.5D;
		for (int i = 0; i < height; ++i) {
			if (rand.nextDouble() < machine_electrolyzer_particles) {
				double x = centerX + (rand.nextBoolean() ? 1D : -1D) * (0.5D + 0.125 * rand.nextDouble());
				double y = minCenterY + i + 1 - 2 * rand.nextDouble();
				double z = centerZ + (rand.nextBoolean() ? 1D : -1D) * (0.5D + 0.125 * rand.nextDouble());
				getWorld().spawnParticle(EnumParticleTypes.WATER_BUBBLE, false, x, y, z, 0D, 0D, 0D);
			}
		}
	}
	
	// NBT
	
	@Override
	public void writeToLogicTag(NBTTagCompound logicTag, SyncReason syncReason) {
		super.writeToLogicTag(logicTag, syncReason);
		logicTag.setDouble("electrolyteEfficiency", electrolyteEfficiency);
	}
	
	@Override
	public void readFromLogicTag(NBTTagCompound logicTag, SyncReason syncReason) {
		super.readFromLogicTag(logicTag, syncReason);
		electrolyteEfficiency = logicTag.getDouble("electrolyteEfficiency");
	}
	
	// Packets
	
	@Override
	public MachineUpdatePacket getMultiblockUpdatePacket() {
		return new ElectrolyzerUpdatePacket(multiblock.controller.getTilePos(), multiblock.isMachineOn, isProcessing, time, baseProcessTime, baseProcessPower, tanks, baseSpeedMultiplier, basePowerMultiplier, electrolyteEfficiency);
	}
	
	@Override
	public void onMultiblockUpdatePacket(MachineUpdatePacket message) {
		super.onMultiblockUpdatePacket(message);
		if (message instanceof ElectrolyzerUpdatePacket packet) {
			electrolyteEfficiency = packet.electrolyteEfficiency;
		}
	}
	
	@Override
	public ElectrolyzerRenderPacket getRenderPacket() {
		return new ElectrolyzerRenderPacket(multiblock.controller.getTilePos(), multiblock.isMachineOn, isProcessing, reservoirTanks);
	}
	
	@Override
	public void onRenderPacket(MachineRenderPacket message) {
		super.onRenderPacket(message);
		if (message instanceof ElectrolyzerRenderPacket packet) {
			boolean wasProcessing = isProcessing;
			isProcessing = packet.isProcessing;
			if (wasProcessing != isProcessing) {
				multiblock.refreshSounds = true;
			}
			TankInfo.readInfoList(packet.reservoirTankInfos, reservoirTanks);
		}
	}
}
