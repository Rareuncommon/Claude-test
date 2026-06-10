package com.rareuncommon.skyblockvaluealerts.client;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import com.rareuncommon.skyblockvaluealerts.SkyblockValueAlerts;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** Renders alert boxes in the top right corner of the HUD and plays the alert sound. */
public final class NotificationOverlay {
	/** How long each notification stays on screen. */
	public static final long DURATION_MS = 10_000;

	private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath(SkyblockValueAlerts.MOD_ID, "notifications");
	private static final int MARGIN = 4;
	private static final int PADDING = 5;
	private static final int ACCENT_WIDTH = 2;
	private static final int BACKGROUND_COLOR = 0xD8101010;
	private static final int ACCENT_COLOR = 0xFFFFAA00;
	private static final int TITLE_COLOR = 0xFFFFAA00;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int VALUE_COLOR = 0xFF55FFFF;

	private static final List<Notification> NOTIFICATIONS = new CopyOnWriteArrayList<>();

	private NotificationOverlay() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE, HUD_ELEMENT_ID, NotificationOverlay::render);
	}

	/** Adds a notification and plays the alert sound. Must be called on the render thread. */
	public static void push(ItemStack stack, int count, double unitValue, double totalValue, String source) {
		Component name = count > 1
				? Component.literal(count + "x ").append(stack.getHoverName())
				: stack.getHoverName().copy();
		Component value = Component.literal(formatCoins(totalValue) + " coins (" + source
				+ (count > 1 ? ", " + formatCoins(unitValue) + " each" : "") + ")");
		NOTIFICATIONS.add(new Notification(Component.literal("Valuable pickup!"), name, value,
				System.currentTimeMillis() + DURATION_MS));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (NOTIFICATIONS.isEmpty()) return;
		long now = System.currentTimeMillis();
		NOTIFICATIONS.removeIf(notification -> now >= notification.expiresAt());

		Font font = Minecraft.getInstance().font;
		int lineHeight = font.lineHeight + 2;
		int y = MARGIN;
		for (Notification notification : NOTIFICATIONS) {
			int width = ACCENT_WIDTH + PADDING * 2 + Math.max(font.width(notification.title()),
					Math.max(font.width(notification.name()), font.width(notification.value())));
			int height = PADDING * 2 + lineHeight * 3 - 2;
			int x = graphics.guiWidth() - MARGIN - width;

			graphics.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
			graphics.fill(x, y, x + ACCENT_WIDTH, y + height, ACCENT_COLOR);

			int textX = x + ACCENT_WIDTH + PADDING;
			int textY = y + PADDING;
			graphics.text(font, notification.title(), textX, textY, TITLE_COLOR, true);
			graphics.text(font, notification.name(), textX, textY + lineHeight, NAME_COLOR, true);
			graphics.text(font, notification.value(), textX, textY + lineHeight * 2, VALUE_COLOR, true);

			y += height + MARGIN;
		}
	}

	static String formatCoins(double coins) {
		if (coins >= 1_000_000_000) return String.format(Locale.ROOT, "%.2fB", coins / 1_000_000_000);
		if (coins >= 1_000_000) return String.format(Locale.ROOT, "%.2fM", coins / 1_000_000);
		if (coins >= 1_000) return String.format(Locale.ROOT, "%.1fk", coins / 1_000);
		return String.format(Locale.ROOT, "%.0f", coins);
	}

	private record Notification(Component title, Component name, Component value, long expiresAt) {
	}
}
