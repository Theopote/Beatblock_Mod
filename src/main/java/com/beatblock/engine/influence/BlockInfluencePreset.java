package com.beatblock.engine.influence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 维度通道组合模板，对应第 2 层 {@code animationTypeId} 的语义（期 2 由求值器执行）。
 */
public final class BlockInfluencePreset {

	private final String id;
	private final String displayName;
	private final float defaultDurationSeconds;
	private final List<ChannelSpec> channels;

	public BlockInfluencePreset(
		String id,
		String displayName,
		float defaultDurationSeconds,
		List<ChannelSpec> channels
	) {
		this.id = id != null ? id : "unknown";
		this.displayName = displayName != null ? displayName : this.id;
		this.defaultDurationSeconds = Math.max(0.01f, defaultDurationSeconds);
		this.channels = channels != null
			? List.copyOf(channels)
			: List.of();
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public float getDefaultDurationSeconds() {
		return defaultDurationSeconds;
	}

	public List<ChannelSpec> getChannels() {
		return channels;
	}

	public List<ChannelSpec> channelsFor(InfluenceDimension dimension) {
		if (dimension == null || channels.isEmpty()) {
			return List.of();
		}
		List<ChannelSpec> matched = new ArrayList<>();
		for (ChannelSpec channel : channels) {
			if (channel != null && channel.dimension() == dimension && channel.enabled()) {
				matched.add(channel);
			}
		}
		return Collections.unmodifiableList(matched);
	}

	public static Builder builder(String id, String displayName) {
		return new Builder(id, displayName);
	}

	public static final class Builder {
		private final String id;
		private final String displayName;
		private float defaultDurationSeconds = 1f;
		private final List<ChannelSpec> channels = new ArrayList<>();

		private Builder(String id, String displayName) {
			this.id = id;
			this.displayName = displayName;
		}

		public Builder durationSeconds(float seconds) {
			this.defaultDurationSeconds = seconds;
			return this;
		}

		public Builder channel(ChannelSpec channel) {
			if (channel != null) channels.add(channel);
			return this;
		}

		public BlockInfluencePreset build() {
			return new BlockInfluencePreset(id, displayName, defaultDurationSeconds, channels);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof BlockInfluencePreset other)) return false;
		return Objects.equals(id, other.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
