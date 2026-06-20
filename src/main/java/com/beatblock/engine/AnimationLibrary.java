package com.beatblock.engine;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动画模板库：由 {@link BlockInfluencePresets} 内置 preset 注册。
 */
public final class AnimationLibrary {

	private final Map<String, AnimationDefinition> animations = new LinkedHashMap<>();

	public AnimationLibrary() {
		registerBuiltIns();
	}

	private void registerBuiltIns() {
		for (BlockInfluencePreset preset : BlockInfluencePresets.getAll().values()) {
			register(new AnimationDefinition(preset));
		}
	}

	public void register(AnimationDefinition definition) {
		if (definition != null) animations.put(definition.getId(), definition);
	}

	public AnimationDefinition get(String id) {
		return animations.get(id);
	}

	public Map<String, AnimationDefinition> getAll() {
		return Collections.unmodifiableMap(animations);
	}
}
