package com.rareuncommon.skyblockvaluealerts.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rareuncommon.skyblockvaluealerts.SkyblockValueAlerts;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically fetches Hypixel bazaar prices and SkyBlock auction house lowest BINs,
 * keeping an in-memory cache so lookups on item pickup are instant and never block
 * the render thread.
 */
public final class PriceService {
	/** Official Hypixel API; the bazaar endpoint is public and requires no API key. */
	private static final URI BAZAAR_URI = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	/** Aggregated auction house lowest buy-it-now prices (the backend used by Skyblocker). */
	private static final URI LOWEST_BINS_URI = URI.create("https://hysky.de/api/auctions/lowestbins");
	private static final long REFRESH_INTERVAL_MINUTES = 5;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

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

	private PriceService() {
	}

	public static void start() {
		SCHEDULER.scheduleAtFixedRate(PriceService::refresh, 0, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
	}

	/** {@return the unit price for instantly selling this product to the bazaar, if it is a bazaar product} */
	public static OptionalDouble bazaarValue(String skyblockId) {
		return lookup(bazaarInstantSell, skyblockId);
	}

	/** {@return the cheapest current buy-it-now price for this item on the auction house, if it has one} */
	public static OptionalDouble lowestBin(String skyblockId) {
		return lookup(lowestBins, skyblockId);
	}

	private static OptionalDouble lookup(Map<String, Double> prices, String skyblockId) {
		Double price = prices.get(skyblockId);
		return price == null ? OptionalDouble.empty() : OptionalDouble.of(price);
	}

	private static void refresh() {
		try {
			bazaarInstantSell = fetchBazaar();
		} catch (Exception e) {
			SkyblockValueAlerts.LOGGER.warn("Failed to refresh bazaar prices", e);
		}
		try {
			lowestBins = fetchLowestBins();
		} catch (Exception e) {
			SkyblockValueAlerts.LOGGER.warn("Failed to refresh auction lowest BINs", e);
		}
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
