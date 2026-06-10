package com.rareuncommon.skyblockvaluealerts.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * Detects items picked up directly into sacks by parsing the compact
 * {@code [Sacks] +N items} chat notification Hypixel sends; the per-item
 * breakdown lives in the message's hover text, in lines like
 * {@code +20 Enchanted Diamond (Mining Sack)}.
 */
public final class SackAlerts {
	private static final String SACKS_MESSAGE_START = "[Sacks]";
	private static final Pattern CHANGE_PATTERN = Pattern.compile("([+-])([\\d,]+) (.+) \\((.+)\\)");

	private SackAlerts() {
	}

	public static void init() {
		ClientReceiveMessageEvents.GAME.register(SackAlerts::onMessage);
	}

	private static void onMessage(Component message, boolean overlay) {
		if (overlay) return;
		String stripped = ChatFormatting.stripFormatting(message.getString());
		if (stripped == null || !stripped.startsWith(SACKS_MESSAGE_START)) return;
		if (message.getSiblings().isEmpty()) return;
		if (!(message.getSiblings().getFirst().getStyle().getHoverEvent() instanceof HoverEvent.ShowText(Component hoverText))) return;
		String hover = ChatFormatting.stripFormatting(hoverText.getString());
		if (hover == null) return;

		Matcher matcher = CHANGE_PATTERN.matcher(hover);
		while (matcher.find()) {
			if (!matcher.group(1).equals("+")) continue;
			int count;
			try {
				count = Integer.parseInt(matcher.group(2).replace(",", ""));
			} catch (NumberFormatException e) {
				continue;
			}
			String itemName = matcher.group(3).trim();
			String marketId = PriceService.idForItemName(itemName);
			if (marketId == null) continue;
			ItemValueChecker.onSackItemsGained(marketId, itemName, count);
		}
	}
}
