package dev.cammiescorner.cammiesminecarttweaks.integration;

import dev.cammiescorner.cammiesminecarttweaks.MinecartTweaks;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.BoundedDiscrete;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject;

@Config(name = MinecartTweaks.MOD_ID)
public class MinecartTweaksConfig implements ConfigData {
	@CollapsibleObject public ClientTweaks clientTweaks = new ClientTweaks();
	@CollapsibleObject public CommonTweaks commonTweaks = new CommonTweaks();

	public double getMinecartBaseSpeed() {
		return Math.max(0.1, commonTweaks.minecartBaseSpeed);
	}

	public double getFurnaceSpeedMultiplier() {
		return Math.max(0.1, commonTweaks.furnaceSpeedMultiplier);
	}

	public double getMaxSpeedAroundTurns() {
		return Math.min(1, commonTweaks.maxSpeedAroundTurns);
	}

	public static class ClientTweaks {
		public boolean useCampfireSmoke = true;
		public boolean playerViewIsLocked = false;
		@BoundedDiscrete(max = 90L) public int maxViewAngle = 90;
	}

	public static class CommonTweaks {
		public double furnaceSpeedMultiplier = 2D;
		public double minecartBaseSpeed = 0.5D;
		public double maxSpeedAroundTurns = 0.4D;
		public float minecartDamage = 20F;
		public int furnaceMaxBurnTime = 72000;
		public boolean canLinkMinecarts = true;
		public boolean shouldPoweredRailsStopFurnace = true;
		public boolean furnacesCanUseAllFuels = true;
	}
}
