package com.beatblock.engine;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 独立 StageObject（非 BuildLayer 拥有）↔ JSON（.osc {@code stageObjects[]}）。
 * <p>
 * BuildLayer 内嵌的 StageObject 仍由 {@link com.beatblock.engine.layer.BuildLayerPersistence} 持久化；
 * 此处只保存 Quick Start / ToolPanel 等路径创建的裸对象，避免重复写入 blocks。
 */
public final class StageObjectPersistence {

	private StageObjectPersistence() {}

	public static Set<String> collectLayerOwnedStageIds(BuildLayerManager manager) {
		Set<String> ids = new LinkedHashSet<>();
		if (manager == null) {
			return ids;
		}
		for (BuildLayer layer : manager.getAll()) {
			String stageId = layer.getStageObjectId();
			if (stageId != null && !stageId.isBlank()) {
				ids.add(stageId);
			}
		}
		return ids;
	}

	public static JsonArray toJson(StageObjectSystem system, Set<String> layerOwnedStageIds) {
		JsonArray arr = new JsonArray();
		if (system == null) {
			return arr;
		}
		Set<String> owned = layerOwnedStageIds != null ? layerOwnedStageIds : Set.of();
		for (RuntimeStageObject obj : system.getAll()) {
			if (obj == null || owned.contains(obj.getId())) {
				continue;
			}
			arr.add(stageObjectToJson(obj));
		}
		return arr;
	}

	/**
	 * 在 BuildLayer 恢复之后调用：移除旧会话中的独立对象，再注册 JSON 中的条目。
	 */
	public static void loadInto(
		StageObjectSystem system,
		JsonArray arr,
		Set<String> layerOwnedStageIds
	) {
		if (system == null) {
			return;
		}
		Set<String> owned = layerOwnedStageIds != null ? layerOwnedStageIds : Set.of();
		purgeStandaloneObjects(system, owned);
		if (arr == null) {
			return;
		}
		for (int i = 0; i < arr.size(); i++) {
			RuntimeStageObject obj = stageObjectFromJson(arr.get(i).getAsJsonObject());
			if (obj == null) {
				continue;
			}
			if (owned.contains(obj.getId())) {
				BeatBlock.LOGGER.warn(
					"Skipping standalone stageObject {} in .osc: id already owned by a BuildLayer",
					obj.getId()
				);
				continue;
			}
			system.register(obj);
		}
	}

	private static void purgeStandaloneObjects(StageObjectSystem system, Set<String> layerOwnedStageIds) {
		List<String> toRemove = new ArrayList<>();
		for (RuntimeStageObject obj : system.getAll()) {
			if (obj != null && !layerOwnedStageIds.contains(obj.getId())) {
				toRemove.add(obj.getId());
			}
		}
		for (String id : toRemove) {
			system.remove(id);
		}
	}

	private static JsonObject stageObjectToJson(RuntimeStageObject obj) {
		JsonObject root = new JsonObject();
		root.addProperty("id", obj.getId());
		root.addProperty("name", obj.getName());
		JsonArray blocks = new JsonArray();
		for (BlockPos pos : obj.getBlocks()) {
			blocks.add(posToJson(pos));
		}
		root.add("blocks", blocks);
		root.add("groupSpec", groupSpecToJson(obj.getGroupSpec()));
		return root;
	}

	private static RuntimeStageObject stageObjectFromJson(JsonObject root) {
		if (root == null || !root.has("id")) {
			return null;
		}
		String id = root.get("id").getAsString();
		if (id == null || id.isBlank()) {
			return null;
		}
		String name = root.has("name") ? root.get("name").getAsString() : id;
		List<BlockPos> blocks = new ArrayList<>();
		if (root.has("blocks")) {
			JsonArray arr = root.getAsJsonArray("blocks");
			for (int i = 0; i < arr.size(); i++) {
				BlockPos pos = posFromJson(arr.get(i).getAsJsonObject());
				if (pos != null) {
					blocks.add(pos);
				}
			}
		}
		if (blocks.isEmpty()) {
			return null;
		}
		GroupSpec groupSpec = root.has("groupSpec")
			? groupSpecFromJson(root.getAsJsonObject("groupSpec"))
			: GroupSpec.manualSnapshot();
		return StageObjectSystem.fromBlocks(id, name, blocks, groupSpec);
	}

	private static JsonObject groupSpecToJson(GroupSpec spec) {
		GroupSpec effective = spec != null ? spec : GroupSpec.manualSnapshot();
		JsonObject root = new JsonObject();
		root.addProperty("sourceType", effective.getSourceType());
		root.addProperty("sortingStrategy", effective.getSortingStrategy().name());
		root.addProperty("staggerDelaySeconds", effective.getStaggerDelaySeconds());
		if (!effective.getSourceParams().isEmpty()) {
			root.add("sourceParams", paramsToJson(effective.getSourceParams()));
		}
		return root;
	}

	private static GroupSpec groupSpecFromJson(JsonObject root) {
		if (root == null) {
			return GroupSpec.manualSnapshot();
		}
		String sourceType = root.has("sourceType") ? root.get("sourceType").getAsString() : "manual_snapshot";
		GroupSortingStrategy sorting = GroupSortingStrategy.SEQUENTIAL;
		if (root.has("sortingStrategy")) {
			try {
				sorting = GroupSortingStrategy.valueOf(root.get("sortingStrategy").getAsString());
			} catch (IllegalArgumentException e) {
				BeatBlock.LOGGER.debug("Unknown groupSpec sortingStrategy in .osc, using SEQUENTIAL", e);
			}
		}
		double stagger = root.has("staggerDelaySeconds") ? root.get("staggerDelaySeconds").getAsDouble() : 0.0;
		Map<String, Object> params = root.has("sourceParams")
			? paramsFromJson(root.getAsJsonObject("sourceParams"))
			: Map.of();
		return new GroupSpec(sourceType, params, sorting, stagger);
	}

	private static JsonObject paramsToJson(Map<String, Object> params) {
		JsonObject obj = new JsonObject();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Number number) {
				obj.addProperty(entry.getKey(), number);
			} else if (value instanceof Boolean bool) {
				obj.addProperty(entry.getKey(), bool);
			} else if (value instanceof Map<?, ?> map) {
				obj.add(entry.getKey(), nestedMapToJson(map));
			} else if (value != null) {
				obj.addProperty(entry.getKey(), String.valueOf(value));
			}
		}
		return obj;
	}

	private static JsonObject nestedMapToJson(Map<?, ?> map) {
		JsonObject obj = new JsonObject();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() == null) {
				continue;
			}
			String key = String.valueOf(entry.getKey());
			Object value = entry.getValue();
			if (value instanceof Number number) {
				obj.addProperty(key, number);
			} else if (value instanceof Boolean bool) {
				obj.addProperty(key, bool);
			} else if (value != null) {
				obj.addProperty(key, String.valueOf(value));
			}
		}
		return obj;
	}

	private static Map<String, Object> paramsFromJson(JsonObject root) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (root == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			JsonElement value = entry.getValue();
			if (value == null || value.isJsonNull()) {
				continue;
			}
			if (value.isJsonPrimitive()) {
				var primitive = value.getAsJsonPrimitive();
				if (primitive.isBoolean()) {
					out.put(entry.getKey(), primitive.getAsBoolean());
				} else if (primitive.isNumber()) {
					out.put(entry.getKey(), primitive.getAsNumber());
				} else {
					out.put(entry.getKey(), primitive.getAsString());
				}
			} else if (value.isJsonObject()) {
				out.put(entry.getKey(), nestedMapFromJson(value.getAsJsonObject()));
			}
		}
		return out;
	}

	private static Map<String, Object> nestedMapFromJson(JsonObject root) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			JsonElement value = entry.getValue();
			if (value != null && value.isJsonPrimitive()) {
				var primitive = value.getAsJsonPrimitive();
				if (primitive.isNumber()) {
					out.put(entry.getKey(), primitive.getAsNumber());
				} else if (primitive.isBoolean()) {
					out.put(entry.getKey(), primitive.getAsBoolean());
				} else {
					out.put(entry.getKey(), primitive.getAsString());
				}
			}
		}
		return out;
	}

	private static JsonObject posToJson(BlockPos pos) {
		JsonObject o = new JsonObject();
		o.addProperty("x", pos.getX());
		o.addProperty("y", pos.getY());
		o.addProperty("z", pos.getZ());
		return o;
	}

	private static BlockPos posFromJson(JsonObject o) {
		if (o == null || !o.has("x")) {
			return null;
		}
		return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
	}
}
