package com.rareuncommon.skyblockvaluealerts.client;

import com.rareuncommon.skyblockvaluealerts.SkyblockValueAlerts;

import net.fabricmc.api.ClientModInitializer;

public class SkyblockValueAlertsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PriceService.start();
		NotificationOverlay.init();
		SkyblockValueAlerts.LOGGER.info("SkyBlock Value Alerts client ready, watching pickups worth over {} coins.",
				NotificationOverlay.formatCoins(ItemValueChecker.VALUE_THRESHOLD));
	}
}
