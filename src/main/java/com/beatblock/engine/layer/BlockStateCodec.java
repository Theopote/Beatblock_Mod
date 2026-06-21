package com.beatblock.engine.layer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/**
 * BlockState ↔ JSON 序列化（用于 .osc 持久化 capturedStates）。
 */
public final class BlockStateCodec {

	private BlockStateCodec() {}

	public static JsonObject toJson(BlockState state) {
		JsonObject root = new JsonObject();
		if (state == null) return root;
		root.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());
		JsonObject props = new JsonObject();
		for (Property<?> property : state.getProperties()) {
			props.addProperty(property.getName(), stringify(property, state));
		}
		if (!props.isEmpty()) root.add("properties", props);
		return root;
	}

	public static BlockState fromJson(JsonObject root) {
		if (root == null || !root.has("block")) return null;
		String blockIdRaw = root.get("block").getAsString();
		Identifier blockId;
		try {
			blockId = Identifier.of(blockIdRaw);
		} catch (Exception ex) {
			return null;
		}
		if (!Registries.BLOCK.containsId(blockId)) return null;
		Block block = Registries.BLOCK.get(blockId);
		BlockState state = block.getDefaultState();
		if (!root.has("properties") || root.get("properties").isJsonNull()) return state;
		JsonObject props = root.getAsJsonObject("properties");
		for (Property<?> property : state.getProperties()) {
			if (!props.has(property.getName())) continue;
			state = withProperty(state, property, props.get(property.getName()).getAsString());
		}
		return state;
	}

	public static BlockState fromJsonString(String json) {
		if (json == null || json.isBlank()) return null;
		try {
			return fromJson(JsonParser.parseString(json).getAsJsonObject());
		} catch (Exception ex) {
			return null;
		}
	}

	private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String raw) {
		if (state == null || property == null || raw == null) return state;
		return property.parse(raw).map(value -> state.with(property, value)).orElse(state);
	}

	private static <T extends Comparable<T>> String stringify(Property<T> property, BlockState state) {
		T value = state.get(property);
		return property.name(value);
	}
}
