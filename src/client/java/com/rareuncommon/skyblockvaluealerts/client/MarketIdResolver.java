package com.rareuncommon.skyblockvaluealerts.client;

import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rareuncommon.skyblockvaluealerts.SkyblockValueAlerts;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Turns an item stack into the id used by the bazaar and auction price APIs.
 * Most items use their plain SkyBlock id, but some categories list under a
 * derived id (enchanted books, pets, potions, runes, shiny variants).
 */
public final class MarketIdResolver {
	private MarketIdResolver() {
	}

	/** {@return the bazaar/auction API id for the stack, or an empty string if it is not a SkyBlock item} */
	public static String resolve(ItemStack stack) {
		CompoundTag customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag attributes = customData;
		String id = attributes.getStringOr("id", "");
		if (id.isEmpty()) {
			// Items converted from legacy servers nest their attributes under ExtraAttributes.
			attributes = customData.getCompoundOrEmpty("ExtraAttributes");
			id = attributes.getStringOr("id", "");
		}
		if (id.isEmpty()) return "";

		if (attributes.contains("is_shiny")) return "SHINY_" + id;

		switch (id) {
			case "ENCHANTED_BOOK" -> {
				CompoundTag enchantments = attributes.getCompoundOrEmpty("enchantments");
				Optional<String> enchantment = enchantments.keySet().stream().findFirst();
				if (enchantment.isPresent()) {
					return "ENCHANTMENT_" + enchantment.get().toUpperCase(Locale.ENGLISH)
							+ "_" + enchantments.getIntOr(enchantment.get(), 0);
				}
			}
			case "PET" -> {
				String petInfo = attributes.getStringOr("petInfo", "");
				if (!petInfo.isEmpty()) {
					try {
						JsonObject info = JsonParser.parseString(petInfo).getAsJsonObject();
						return "LVL_1_" + info.get("tier").getAsString() + "_" + info.get("type").getAsString();
					} catch (Exception e) {
						SkyblockValueAlerts.LOGGER.debug("Could not parse pet info of {}", id, e);
					}
				}
			}
			case "POTION" -> {
				if (attributes.contains("potion") && attributes.contains("potion_level")) {
					String enhanced = attributes.getBooleanOr("enhanced", false) ? "_ENHANCED" : "";
					String extended = attributes.getBooleanOr("extended", false) ? "_EXTENDED" : "";
					String splash = attributes.getBooleanOr("splash", false) ? "_SPLASH" : "";
					return (attributes.getStringOr("potion", "") + "_POTION_" + attributes.getIntOr("potion_level", 0)
							+ enhanced + extended + splash).toUpperCase(Locale.ENGLISH);
				}
			}
			case "RUNE" -> {
				CompoundTag runes = attributes.getCompoundOrEmpty("runes");
				Optional<String> rune = runes.keySet().stream().findFirst();
				if (rune.isPresent()) {
					return rune.get().toUpperCase(Locale.ENGLISH) + "_RUNE_" + runes.getIntOr(rune.get(), 0);
				}
			}
			default -> {
			}
		}
		return id;
	}
}
