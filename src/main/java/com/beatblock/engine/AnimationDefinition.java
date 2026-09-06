package com.beatblock.engine;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.ChannelSpec;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.timeline.playback.PlaybackSemantics;

/**
 * 动画库中的模板：id、名称、时长、{@link BlockInfluencePreset} 通道组合。
 * <p>
 * UI catalog should prefer this (or {@code AnimationLibraryItem}) over binding
 * panels directly to {@link BlockInfluencePreset}.
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

	/**
	 * First enabled channel dimension; used for Animation Library grouping.
	 */
	public InfluenceDimension getPrimaryDimension() {
		if (preset.getChannels().isEmpty()) {
			return InfluenceDimension.EXISTENCE;
		}
		for (ChannelSpec channel : preset.getChannels()) {
			if (channel != null && channel.enabled()) {
				return channel.dimension();
			}
		}
		return InfluenceDimension.EXISTENCE;
	}

	public java.util.Optional<PlaybackSemantics> getPlaybackSemantics() {
		return preset.getPlaybackSemantics();
	}

	public BlockInfluencePreset getPreset() {
		return preset;
	}
}
