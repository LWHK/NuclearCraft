package nc.item.tool;

import nc.item.IInfoItem;
import nc.util.*;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.*;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.*;

import javax.annotation.Nullable;
import java.util.List;

public class NCShovel extends ItemSpade implements IInfoItem {
	
	private final TextFormatting infoColor;
	private final String[] tooltip;
	public String[] info;
	
	public NCShovel(ToolMaterial material, TextFormatting infoColor, String... tooltip) {
		super(material);
		this.infoColor = infoColor;
		this.tooltip = tooltip;
	}
	
	@Override
	public void setInfo() {
		info = InfoHelper.buildInfo(getTranslationKey(), tooltip);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack itemStack, @Nullable World world, List<String> currentTooltip, ITooltipFlag flag) {
		super.addInformation(itemStack, world, currentTooltip, flag);
		if (info.length > 0) {
			InfoHelper.infoFull(currentTooltip, TextFormatting.RED, InfoHelper.EMPTY_ARRAY, infoColor, info);
		}
	}
	
	@Override
	public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
		ItemStack mat = toolMaterial.getRepairItemStack();
		return mat != null && !mat.isEmpty() && OreDictHelper.isOreMatching(mat, repair);
	}
}
