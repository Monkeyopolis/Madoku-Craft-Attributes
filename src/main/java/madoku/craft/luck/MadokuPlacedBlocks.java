package madoku.craft.luck;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.data.MadokuData;
import madoku.craft.scheduler.MadokuScheduler;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MadokuPlacedBlocks {
	private static final String DATA_FOLDER_NAME = "madoku-craft-luck";
	private static final String DATA_FILE_NAME = "madoku-placed-blocks";
	private static final String FIELD_LEVELS = "levels";
	private static final String FIELD_LEVEL_ID = "level_id";
	private static final String FIELD_POSITIONS = "positions";
	private static final String FIELD_POSITION = "position";
	private static final String FIELD_TRACKED_SINCE_GAMEPLAY_TICK = "tracked_since_gameplay_tick";
	private static final long AUTOSAVE_INTERVAL_TICKS = 60L * 20L;
	private static final long PLACED_BLOCK_RETENTION_DAYS = 28L;

	private static final Map<String, Set<Long>> PLACED_BLOCKS_BY_LEVEL = new HashMap<>();
	private static boolean dirty = false;
	private static long lastAutosaveBucket = Long.MIN_VALUE;
	private static long trackedSinceGameplayTick = -1L;

	private MadokuPlacedBlocks() {
	}

	public static void initialize() {
	}

	public static void reset() {
		PLACED_BLOCKS_BY_LEVEL.clear();
		dirty = false;
		lastAutosaveBucket = Long.MIN_VALUE;
		trackedSinceGameplayTick = -1L;
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		MadokuData.createWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(MadokuData.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME));
		clearExpiredPlacedBlocks(null);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), AUTOSAVE_INTERVAL_TICKS);
		if (bucket == lastAutosaveBucket) {
			return;
		}

		lastAutosaveBucket = bucket;
		clearExpiredPlacedBlocks(null);
		if (dirty) {
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}

		clearExpiredPlacedBlocks(null);
		MadokuData.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
	}

	public static void recordPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}

		clearExpiredPlacedBlocks(level);

		String levelId = levelId(level);
		if (levelId.isBlank()) {
			return;
		}

		if (!hasTrackedBlocks()) {
			trackedSinceGameplayTick = MadokuTicks.getGameplayTicks();
		}

		Set<Long> positions = PLACED_BLOCKS_BY_LEVEL.computeIfAbsent(levelId, ignored -> new HashSet<>());
		if (!positions.add(pos.asLong())) {
			return;
		}

		dirty = true;
		MadokuLuck.emitLuckDebug(
			"luck.place_recorded",
			level,
			pos,
			"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
			Map.of(
				"tracked_count", Integer.toString(positions.size()),
				"tracked_since_gameplay_tick", Long.toString(Math.max(0L, trackedSinceGameplayTick))
			)
		);
	}

	public static boolean isPlayerPlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}

		clearExpiredPlacedBlocks(level);

		Set<Long> positions = PLACED_BLOCKS_BY_LEVEL.get(levelId(level));
		boolean tracked = positions != null && positions.contains(pos.asLong());
		MadokuLuck.emitLuckDebug(
			"luck.place_lookup",
			level,
			pos,
			"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
			Map.of(
				"tracked", Boolean.toString(tracked),
				"tracked_count", Integer.toString(positions == null ? 0 : positions.size())
			)
		);
		return tracked;
	}

	public static boolean consumePlacedBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return false;
		}

		clearExpiredPlacedBlocks(level);

		String levelId = levelId(level);
		Set<Long> positions = PLACED_BLOCKS_BY_LEVEL.get(levelId);
		if (positions == null || !positions.remove(pos.asLong())) {
			return false;
		}

		if (positions.isEmpty()) {
			PLACED_BLOCKS_BY_LEVEL.remove(levelId);
		}
		if (!hasTrackedBlocks()) {
			trackedSinceGameplayTick = -1L;
		}

		dirty = true;
		MadokuLuck.emitLuckDebug(
			"luck.place_consumed",
			level,
			pos,
			"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ(),
			Map.of("remaining_count", Integer.toString(positions.size()))
		);
		return true;
	}

	private static String levelId(ServerLevel world) {
		if (world == null) {
			return "";
		}
		return MadokuScheduler.normalizeLevelIdentifier(world.dimension().toString());
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add(FIELD_LEVELS, new JsonArray());
		root.addProperty(FIELD_TRACKED_SINCE_GAMEPLAY_TICK, -1L);
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_TRACKED_SINCE_GAMEPLAY_TICK, trackedSinceGameplayTick);
		JsonArray levels = new JsonArray();
		for (Map.Entry<String, Set<Long>> entry : PLACED_BLOCKS_BY_LEVEL.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isEmpty()) {
				continue;
			}

			JsonObject level = new JsonObject();
			level.addProperty(FIELD_LEVEL_ID, entry.getKey());
			JsonArray positions = new JsonArray();
			for (Long position : entry.getValue()) {
				if (position == null) {
					continue;
				}

				JsonObject positionObject = new JsonObject();
				positionObject.addProperty(FIELD_POSITION, position);
				positions.add(positionObject);
			}
			level.add(FIELD_POSITIONS, positions);
			levels.add(level);
		}
		root.add(FIELD_LEVELS, levels);
		return root;
	}

	private static void applyPersistedData(JsonObject source) {
		PLACED_BLOCKS_BY_LEVEL.clear();
		trackedSinceGameplayTick = -1L;
		if (source == null) {
			return;
		}

		long nowGameplayTick = MadokuTicks.getGameplayTicks();
		trackedSinceGameplayTick = readLong(source, FIELD_TRACKED_SINCE_GAMEPLAY_TICK, -1L);
		if (!source.has(FIELD_LEVELS) || !source.get(FIELD_LEVELS).isJsonArray()) {
			return;
		}

		JsonArray levels = source.getAsJsonArray(FIELD_LEVELS);
		for (JsonElement levelElement : levels) {
			if (levelElement == null || !levelElement.isJsonObject()) {
				continue;
			}

			JsonObject levelObject = levelElement.getAsJsonObject();
			String levelId = readString(levelObject, FIELD_LEVEL_ID);
			if (levelId.isBlank() || !levelObject.has(FIELD_POSITIONS) || !levelObject.get(FIELD_POSITIONS).isJsonArray()) {
				continue;
			}

			Set<Long> positions = new HashSet<>();
			for (JsonElement positionElement : levelObject.getAsJsonArray(FIELD_POSITIONS)) {
				if (positionElement == null) {
					continue;
				}
				if (positionElement.isJsonPrimitive() && positionElement.getAsJsonPrimitive().isNumber()) {
					positions.add(positionElement.getAsLong());
					continue;
				}
				if (!positionElement.isJsonObject()) {
					continue;
				}

				long position = readLong(positionElement.getAsJsonObject(), FIELD_POSITION, Long.MIN_VALUE);
				if (position != Long.MIN_VALUE) {
					positions.add(position);
				}
			}

			if (!positions.isEmpty()) {
				PLACED_BLOCKS_BY_LEVEL.put(levelId, positions);
			}
		}

		if (hasTrackedBlocks() && trackedSinceGameplayTick < 0L) {
			trackedSinceGameplayTick = nowGameplayTick;
			dirty = true;
		}
	}

	private static String readString(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key)) {
			return "";
		}
		JsonElement element = object.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
			? element.getAsString().trim()
			: "";
	}

	private static long readLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || !object.has(key)) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean clearExpiredPlacedBlocks(ServerLevel level) {
		if (!hasTrackedBlocks() || trackedSinceGameplayTick < 0L) {
			if (!hasTrackedBlocks()) {
				trackedSinceGameplayTick = -1L;
			}
			return false;
		}

		long expiresAfterTicks = resolvePlacedBlockRetentionTicks();
		long nowGameplayTick = MadokuTicks.getGameplayTicks();
		if (nowGameplayTick - trackedSinceGameplayTick < expiresAfterTicks) {
			return false;
		}

		int expiredLevels = PLACED_BLOCKS_BY_LEVEL.size();
		int expiredBlocks = totalTrackedBlocks();
		PLACED_BLOCKS_BY_LEVEL.clear();
		trackedSinceGameplayTick = -1L;
		dirty = true;
		MadokuLuck.emitLuckDebug(
			"luck.place_list_expired",
			level,
			null,
			"global",
			Map.of(
				"expired_levels", Integer.toString(expiredLevels),
				"expired_blocks", Integer.toString(expiredBlocks),
				"expiry_ticks", Long.toString(expiresAfterTicks)
			)
		);
		return true;
	}

	private static boolean hasTrackedBlocks() {
		return totalTrackedBlocks() > 0;
	}

	private static int totalTrackedBlocks() {
		int count = 0;
		for (Set<Long> positions : PLACED_BLOCKS_BY_LEVEL.values()) {
			if (positions != null) {
				count += positions.size();
			}
		}
		return count;
	}

	private static long resolvePlacedBlockRetentionTicks() {
		long dayTicks = Math.max(1L, MadokuTime.getGameplayTicksPerDay());
		try {
			return Math.max(1L, Math.multiplyExact(PLACED_BLOCK_RETENTION_DAYS, dayTicks));
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
	}
}
