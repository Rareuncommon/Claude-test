package com.rareuncommon.skyblockvaluealerts.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Detects items gained by diffing the player's inventory every tick. This catches the many
 * SkyBlock acquisitions that never spawn a ground item: drops teleported straight to the
 * inventory, menu purchases, auction claims, crafting, and so on.
 *
 * <p>To avoid false alerts when the player merely moves their own items out of a chest,
 * gains sit in a short settle window and are offset against items that visibly left the
 * open container during the same period.
 */
public final class InventoryMonitor {
	/** Ticks a gain waits before alerting, so matching container losses can cancel it. */
	private static final int SETTLE_TICKS = 20;
	/** How long observed container losses stay usable as transfer offsets. */
	private static final int LOSS_TTL_TICKS = 60;
	/** Ticks after joining a world during which gains are ignored while the server populates the inventory. */
	private static final int JOIN_GRACE_TICKS = 100;

	private static long tick;
	private static long graceUntil;
	private static ClientLevel lastLevel;
	private static Map<String, Integer> previousInventory;
	private static AbstractContainerMenu previousMenu;
	private static Map<String, Integer> previousContainer = Map.of();
	private static final Map<String, Gain> pendingGains = new HashMap<>();
	private static final Map<String, Loss> recentContainerLosses = new HashMap<>();

	private InventoryMonitor() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(InventoryMonitor::onTick);
	}

	private static void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			reset();
			lastLevel = null;
			return;
		}
		if (client.level != lastLevel) {
			reset();
			lastLevel = client.level;
		}
		tick++;

		Map<String, Integer> current = new HashMap<>();
		Map<String, ItemStack> samples = new HashMap<>();
		Inventory inventory = client.player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			collect(inventory.getItem(i), current, samples);
		}

		if (previousInventory != null && tick >= graceUntil) {
			for (Map.Entry<String, Integer> entry : current.entrySet()) {
				int gained = entry.getValue() - previousInventory.getOrDefault(entry.getKey(), 0);
				if (gained <= 0) continue;
				Gain gain = pendingGains.computeIfAbsent(entry.getKey(), key -> new Gain(samples.get(key).copy()));
				gain.count += gained;
				gain.lastChangeTick = tick;
			}
		}
		previousInventory = current;

		trackContainer(client);
		flush();
	}

	/** Records items that left the currently open container, so transfers into the inventory don't alert. */
	private static void trackContainer(Minecraft client) {
		AbstractContainerMenu menu = client.player.containerMenu;
		if (menu == client.player.inventoryMenu) {
			previousMenu = null;
			previousContainer = Map.of();
			return;
		}

		Map<String, Integer> counts = new HashMap<>();
		for (Slot slot : menu.slots) {
			if (slot.container == client.player.getInventory()) continue;
			collect(slot.getItem(), counts, null);
		}

		if (menu == previousMenu) {
			for (Map.Entry<String, Integer> entry : previousContainer.entrySet()) {
				int lost = entry.getValue() - counts.getOrDefault(entry.getKey(), 0);
				if (lost <= 0) continue;
				Loss loss = recentContainerLosses.computeIfAbsent(entry.getKey(), key -> new Loss());
				loss.count += lost;
				loss.tick = tick;
			}
		}
		previousMenu = menu;
		previousContainer = counts;
	}

	private static void flush() {
		Iterator<Map.Entry<String, Gain>> iterator = pendingGains.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Gain> entry = iterator.next();
			Gain gain = entry.getValue();
			if (tick - gain.lastChangeTick < SETTLE_TICKS) continue;

			int net = gain.count;
			Loss loss = recentContainerLosses.get(entry.getKey());
			if (loss != null) {
				int offset = Math.min(net, loss.count);
				net -= offset;
				loss.count -= offset;
				if (loss.count <= 0) recentContainerLosses.remove(entry.getKey());
			}
			if (net > 0) {
				ItemValueChecker.onItemPickedUp(gain.sample, net);
			}
			iterator.remove();
		}
		recentContainerLosses.values().removeIf(loss -> tick - loss.tick > LOSS_TTL_TICKS);
	}

	private static void collect(ItemStack stack, Map<String, Integer> counts, Map<String, ItemStack> samples) {
		if (stack == null || stack.isEmpty()) return;
		String marketId = MarketIdResolver.resolve(stack);
		if (marketId.isEmpty()) return;
		counts.merge(marketId, stack.getCount(), Integer::sum);
		if (samples != null) samples.putIfAbsent(marketId, stack);
	}

	private static void reset() {
		previousInventory = null;
		previousMenu = null;
		previousContainer = Map.of();
		pendingGains.clear();
		recentContainerLosses.clear();
		graceUntil = tick + JOIN_GRACE_TICKS;
	}

	private static final class Gain {
		final ItemStack sample;
		int count;
		long lastChangeTick;

		Gain(ItemStack sample) {
			this.sample = sample;
		}
	}

	private static final class Loss {
		int count;
		long tick;
	}
}
