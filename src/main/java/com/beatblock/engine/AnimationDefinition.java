package com.beatblock.engine;

import com.beatblock.engine.influence.BlockInfluencePreset;

/**
 * 动画库中的模板：id、名称、时长、{@link BlockInfluencePreset} 通道组合。
 */
public final class AnimationDefinition {

	private final String id;
	private final String name;
	private final float durationSeconds;
	private final BlockInfluencePreset preset;

	public AnimationDefinition(BlockInfluencePreset preset) {
		if (preset == null) {
			throw new IllegalArgumentException("preset required");
		}
		this.preset = preset;
		this.id = preset.getId();
		this.name = preset.getDisplayName();
		this.durationSeconds = preset.getDefaultDurationSeconds();
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public float getDurationSeconds() {
		return durationSeconds;
	}

	public BlockInfluencePreset getPreset() {
		return preset;
	}
}
