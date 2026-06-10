package com.rareuncommon.skyblockvaluealerts;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyblockValueAlerts implements ModInitializer {
	public static final String MOD_ID = "skyblock_value_alerts";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("SkyBlock Value Alerts initialized.");
	}
}
