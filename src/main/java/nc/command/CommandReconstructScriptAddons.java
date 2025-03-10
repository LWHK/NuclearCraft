package nc.command;

import nc.ModCheck;
import nc.handler.ScriptAddonHandler;
import nc.util.*;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.*;

import java.io.IOException;

public class CommandReconstructScriptAddons extends CommandBase {
	
	@Override
	public String getName() {
		return "nc_reconstruct_script_addons";
	}
	
	@Override
	public String getUsage(ICommandSender sender) {
		return "commands.nuclearcraft.reconstruct_script_addons.usage";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}
	
	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
		try {
			ScriptAddonHandler.init();
			sender.sendMessage(new TextComponentTranslation(TextFormatting.GREEN + Lang.localize("commands.nuclearcraft.reconstruct_script_addons.success")));
			
			if (ModCheck.craftTweakerLoaded() && args.length > 0 && "syntax".equals(args[0])) {
				server.getCommandManager().executeCommand(sender, "/ct syntax");
			}
		} catch (IOException e) {
			sender.sendMessage(new TextComponentTranslation(TextFormatting.RED + Lang.localize("commands.nuclearcraft.reconstruct_script_addons.fail")));
			NCUtil.getLogger().catching(e);
		}
	}
}
