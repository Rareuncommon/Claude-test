package com.rareuncommon.skyblockvaluealerts.client;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Decides whether a picked up item is valuable enough to alert about. */
public final class ItemValueChecker {
	/** Alert when the picked up stack is worth at least this many coins. */
	public static final double VALUE_THRESHOLD = 1_000_000;
	/** Suppress repeat alerts for the same item id within this window, e.g. when collecting a pile of drops. */
	private static final long ALERT_COOLDOWN_MS = 3_000;

	private static final Map<String, Long> lastAlertByItem = new ConcurrentHashMap<>();

	private ItemValueChecker() {
	}

	/** Called on the render thread whenever the local player collects an item entity. */
	public static void onItemPickedUp(ItemStack stack, int amount) {
		if (stack.isEmpty()) return;
		String skyblockId = getSkyblockId(stack);
		if (skyblockId.isEmpty()) return; // not a SkyBlock item

		OptionalDouble bazaar = PriceService.bazaarValue(skyblockId);
		OptionalDouble bin = PriceService.lowestBin(skyblockId);
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

		int count = Math.max(1, amount);
		double totalValue = unitValue * count;
		if (totalValue < VALUE_THRESHOLD) return;

		long now = System.currentTimeMillis();
		Long lastAlert = lastAlertByItem.put(skyblockId, now);
		if (lastAlert != null && now - lastAlert < ALERT_COOLDOWN_MS) return;

		NotificationOverlay.push(stack, count, unitValue, totalValue, source);
	}

	private static String getSkyblockId(ItemStack stack) {
		CompoundTag customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		Optional<String> id = customData.getString("id");
		if (id.isEmpty()) {
			// Items converted from legacy servers nest their attributes under ExtraAttributes.
			id = customData.getCompoundOrEmpty("ExtraAttributes").getString("id");
		}
		return id.orElse("");
	}
}
