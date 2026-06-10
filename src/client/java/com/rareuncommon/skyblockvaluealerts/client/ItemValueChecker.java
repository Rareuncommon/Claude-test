package com.rareuncommon.skyblockvaluealerts.client;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Decides whether gained items are valuable enough to alert about. */
public final class ItemValueChecker {
	/** Alert when the gained items are worth at least this many coins in total. */
	public static final double VALUE_THRESHOLD = 1_000_000;
	/** Suppress repeat alerts for the same item id within this window, e.g. when collecting a pile of drops. */
	private static final long ALERT_COOLDOWN_MS = 3_000;

	private static final Map<String, Long> lastAlertByItem = new ConcurrentHashMap<>();

	private ItemValueChecker() {
	}

	/** Called on the render thread when the player gains items, either as a ground pickup or an inventory gain. */
	public static void onItemPickedUp(ItemStack stack, int amount) {
		if (stack.isEmpty()) return;
		String marketId = MarketIdResolver.resolve(stack);
		if (marketId.isEmpty()) return; // not a SkyBlock item
		check(marketId, stack.getHoverName(), Math.max(1, amount));
	}

	/** Called on the render thread when items are picked up directly into a sack. */
	public static void onSackItemsGained(String marketId, String itemName, int count) {
		check(marketId, Component.literal(itemName), count);
	}

	private static void check(String marketId, Component displayName, int count) {
		OptionalDouble bazaar = PriceService.bazaarValue(marketId);
		OptionalDouble bin = PriceService.lowestBin(marketId);
		if (bazaar.isEmpty() && bin.isEmpty()) return;

		double unitValue;
		String source;
		if (bin.orElse(0) >= bazaar.orElse(0)) {
			unitValue = bin.orElseThrow();
			source = "Lowest BIN";
		} else {
			unitValue = bazaar.orElseThrow();
			source = "Bazaar";
		}

		double totalValue = unitValue * count;
		if (totalValue < VALUE_THRESHOLD) return;

		long now = System.currentTimeMillis();
		Long lastAlert = lastAlertByItem.put(marketId, now);
		if (lastAlert != null && now - lastAlert < ALERT_COOLDOWN_MS) return;

		NotificationOverlay.push(displayName, count, unitValue, totalValue, source);
	}
}
