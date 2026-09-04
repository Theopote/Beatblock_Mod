package com.beatblock.automap.choreography.grammar;

import org.jspecify.annotations.Nullable;

/**
 * 声明式触发条件：决定 {@link ChoreographyPhrase} 何时展开为舞台事件。
 * <p>
 * 当前已实现：{@link OnFeature} / {@link FirstFeature} / {@link EveryNFeatureHits} / {@link EveryNBeats}。
 * 后续可扩展：OnBar / EveryNBar / OnSectionStart / OnPhraseStart / OnDrop / OnEnergyPeak / OnPattern。
 */
public sealed interface TriggerSpec
	permits TriggerSpec.OnFeature,
	TriggerSpec.EveryNBeats,
	TriggerSpec.EveryNFeatureHits,
	TriggerSpec.FirstFeature {

	record OnFeature(
		String normalizedFeatureKey,
		float minEnergy
	) implements TriggerSpec {
		public OnFeature {
			normalizedFeatureKey = normalizedFeatureKey != null ? normalizedFeatureKey : "low";
			minEnergy = Math.max(0f, Math.min(1f, minEnergy));
		}

		public OnFeature(String normalizedFeatureKey) {
			this(normalizedFeatureKey, 0f);
		}
	}

	/**
	 * 基于 Beat Grid：每 {@code beats} 个音乐拍触发一次（从 {@code phaseOffset} 起算）。
	 */
	record EveryNBeats(
		int beats,
		int phaseOffset
	) implements TriggerSpec {
		public EveryNBeats {
			beats = Math.max(1, beats);
			phaseOffset = Math.max(0, phaseOffset);
		}

		public EveryNBeats(int beats) {
			this(beats, 0);
		}
	}

	/**
	 * 基于特征命中序列：每第 {@code interval} 次匹配的 feature 事件触发一次。
	 */
	record EveryNFeatureHits(
		String featureKey,
		int interval
	) implements TriggerSpec {
		public EveryNFeatureHits {
			featureKey = featureKey != null ? featureKey : "kick";
			interval = Math.max(1, interval);
		}
	}

	/**
	 * 有效时间窗内第一次满足能量门槛的 feature 事件（HERO 段落入口 / 高潮点）。
	 */
	record FirstFeature(
		String normalizedFeatureKey,
		float minEnergy
	) implements TriggerSpec {
		public FirstFeature {
			normalizedFeatureKey = normalizedFeatureKey != null ? normalizedFeatureKey : "kick";
			minEnergy = Math.max(0f, Math.min(1f, minEnergy));
		}

		public FirstFeature(String normalizedFeatureKey) {
			this(normalizedFeatureKey, 0f);
		}
	}

	static TriggerSpec fromValue(
		@Nullable String type,
		@Nullable String featureKey,
		int interval,
		float minEnergy
	) {
		return fromValue(type, featureKey, interval, 0, minEnergy);
	}

	static TriggerSpec fromValue(
		@Nullable String type,
		@Nullable String featureKey,
		int intervalOrBeats,
		int phaseOffset,
		float minEnergy
	) {
		if ("every_n_feature_hits".equalsIgnoreCase(type)
			|| "everyNFeatureHits".equalsIgnoreCase(type)) {
			return new EveryNFeatureHits(
				featureKey != null ? featureKey : "kick",
				intervalOrBeats > 0 ? intervalOrBeats : 4
			);
		}
		if ("every_n_beats".equalsIgnoreCase(type) || "everyNBeats".equalsIgnoreCase(type)) {
			// 兼容旧持久化：带 featureKey 的 every_n_beats 实际是 feature-hit 语义。
			if (featureKey != null && !featureKey.isBlank()) {
				return new EveryNFeatureHits(featureKey, intervalOrBeats > 0 ? intervalOrBeats : 4);
			}
			return new EveryNBeats(intervalOrBeats > 0 ? intervalOrBeats : 4, phaseOffset);
		}
		if ("first_feature".equalsIgnoreCase(type) || "firstFeature".equalsIgnoreCase(type)) {
			return new FirstFeature(featureKey != null ? featureKey : "kick", minEnergy);
		}
		return new OnFeature(featureKey != null ? featureKey : "low", minEnergy);
	}
}
