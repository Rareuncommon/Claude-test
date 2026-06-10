package com.rareuncommon.skyblockvaluealerts.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rareuncommon.skyblockvaluealerts.SkyblockValueAlerts;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;

/**
 * Keeps in-memory caches of Hypixel bazaar prices, auction house lowest BINs, and the
 * official item name-to-id index, so lookups on item pickup are instant and never block
 * the render thread.
 *
 * <p>Fresh data is fetched every 5 minutes; failed fetches are retried every 30 seconds.
 * The last known prices are persisted to disk so alerts work immediately on the next
 * launch, even before the first refresh completes.
 */
public final class PriceService {
	/** Official Hypixel API; the bazaar endpoint is public and requires no API key. */
	private static final URI BAZAAR_URI = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	/** Aggregated auction house lowest buy-it-now prices (the backend used by Skyblocker). */
	private static final URI LOWEST_BINS_URI = URI.create("https://hysky.de/api/auctions/lowestbins");
	/** Official item catalogue, used to map display names in sack messages to item ids. */
	private static final URI ITEMS_URI = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
	public static final long REFRESH_INTERVAL_MINUTES = 5;
	private static final long TICK_SECONDS = 30;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
	private static final Path CACHE_FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("skyblock_value_alerts_prices.json");

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "SkyBlock Value Alerts price fetcher");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile Map<String, Double> bazaarInstantSell = Map.of();
	private static volatile Map<String, Double> lowestBins = Map.of();
	private static volatile Map<String, String> itemNameToId = Map.of();

	// Only touched from the scheduler thread.
	private static boolean cacheLoaded;
	private static long bazaarFetchedAt;
	private static long binsFetchedAt;

	private PriceService() {
	}

	public static void start() {
		SCHEDULER.scheduleWithFixedDelay(PriceService::tick, 0, TICK_SECONDS, TimeUnit.SECONDS);
	}

	/** {@return the unit price for instantly selling this product to the bazaar, if it is a bazaar product} */
	public static OptionalDouble bazaarValue(String marketId) {
		Double price = bazaarInstantSell.get(marketId);
		return price == null ? OptionalDouble.empty() : OptionalDouble.of(price);
	}

	/** {@return the cheapest current buy-it-now price for this item on the auction house, if it has one} */
	public static OptionalDouble lowestBin(String marketId) {
		Double price = lowestBins.get(marketId);
		return price == null ? OptionalDouble.empty() : OptionalDouble.of(price);
	}

	/** {@return the item id for an in-game display name, or null if unknown} */
	public static String idForItemName(String displayName) {
		String stripped = ChatFormatting.stripFormatting(displayName);
		if (stripped == null) stripped = displayName;
		return itemNameToId.get(stripped.trim().toLowerCase(Locale.ENGLISH));
	}

	private static void tick() {
		if (!cacheLoaded) {
			cacheLoaded = true;
			loadCache();
		}

		long now = System.currentTimeMillis();
		long maxAgeMillis = TimeUnit.MINUTES.toMillis(REFRESH_INTERVAL_MINUTES);
		boolean updated = false;

		if (now - bazaarFetchedAt >= maxAgeMillis) {
			try {
				bazaarInstantSell = fetchBazaar();
				bazaarFetchedAt = now;
				updated = true;
			} catch (Exception e) {
				SkyblockValueAlerts.LOGGER.warn("Failed to refresh bazaar prices, retrying in {}s: {}", TICK_SECONDS, e.toString());
			}
		}
		if (now - binsFetchedAt >= maxAgeMillis) {
			try {
				lowestBins = fetchLowestBins();
				binsFetchedAt = now;
				updated = true;
			} catch (Exception e) {
				SkyblockValueAlerts.LOGGER.warn("Failed to refresh auction lowest BINs, retrying in {}s: {}", TICK_SECONDS, e.toString());
			}
		}
		if (itemNameToId.isEmpty()) {
			try {
				itemNameToId = fetchItemNames();
			} catch (Exception e) {
				SkyblockValueAlerts.LOGGER.warn("Failed to fetch the item name index, retrying in {}s: {}", TICK_SECONDS, e.toString());
			}
		}

		if (updated) saveCache();
	}

	private static Map<String, Double> fetchBazaar() throws Exception {
		JsonObject root = getJson(BAZAAR_URI).getAsJsonObject();
		Map<String, Double> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> product : root.getAsJsonObject("products").entrySet()) {
			JsonObject quickStatus = product.getValue().getAsJsonObject().getAsJsonObject("quick_status");
			if (quickStatus != null && quickStatus.has("sellPrice")) {
				prices.put(product.getKey(), quickStatus.get("sellPrice").getAsDouble());
			}
		}
		SkyblockValueAlerts.LOGGER.info("Refreshed {} bazaar prices", prices.size());
		return prices;
	}

	private static Map<String, Double> fetchLowestBins() throws Exception {
		Map<String, Double> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : getJson(LOWEST_BINS_URI).getAsJsonObject().entrySet()) {
			prices.put(entry.getKey(), entry.getValue().getAsDouble());
		}
		SkyblockValueAlerts.LOGGER.info("Refreshed {} auction lowest BINs", prices.size());
		return prices;
	}

	private static Map<String, String> fetchItemNames() throws Exception {
		Map<String, String> names = new HashMap<>();
		for (JsonElement element : getJson(ITEMS_URI).getAsJsonObject().getAsJsonArray("items")) {
			JsonObject item = element.getAsJsonObject();
			if (item.has("name") && item.has("id")) {
				names.put(item.get("name").getAsString().trim().toLowerCase(Locale.ENGLISH), item.get("id").getAsString());
			}
		}
		SkyblockValueAlerts.LOGGER.info("Indexed {} item names", names.size());
		return names;
	}

	private static void loadCache() {
		try {
			if (!Files.exists(CACHE_FILE)) return;
			JsonObject root = JsonParser.parseString(Files.readString(CACHE_FILE)).getAsJsonObject();
			Map<String, Double> bazaar = readPrices(root.getAsJsonObject("bazaar"));
			Map<String, Double> bins = readPrices(root.getAsJsonObject("bins"));
			if (!bazaar.isEmpty()) bazaarInstantSell = bazaar;
			if (!bins.isEmpty()) lowestBins = bins;
			SkyblockValueAlerts.LOGGER.info("Loaded {} bazaar prices and {} lowest BINs from the disk cache",
					bazaar.size(), bins.size());
		} catch (Exception e) {
			SkyblockValueAlerts.LOGGER.warn("Failed to load the price cache", e);
		}
	}

	private static void saveCache() {
		try {
			JsonObject root = new JsonObject();
			root.add("bazaar", writePrices(bazaarInstantSell));
			root.add("bins", writePrices(lowestBins));
			Files.writeString(CACHE_FILE, root.toString());
		} catch (Exception e) {
			SkyblockValueAlerts.LOGGER.warn("Failed to save the price cache", e);
		}
	}

	private static Map<String, Double> readPrices(JsonObject object) {
		if (object == null) return Map.of();
		Map<String, Double> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
			prices.put(entry.getKey(), entry.getValue().getAsDouble());
		}
		return prices;
	}

	private static JsonObject writePrices(Map<String, Double> prices) {
		JsonObject object = new JsonObject();
		for (Map.Entry<String, Double> entry : prices.entrySet()) {
			object.addProperty(entry.getKey(), entry.getValue());
		}
		return object;
	}

	private static JsonElement getJson(URI uri) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("User-Agent", "skyblock-value-alerts/1.0.0 (Fabric mod)")
				.GET()
				.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri);
		}
		return JsonParser.parseString(response.body());
	}
}
