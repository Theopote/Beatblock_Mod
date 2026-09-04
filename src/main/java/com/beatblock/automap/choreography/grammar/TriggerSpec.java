package com.beatblock.automap.choreography.grammar;

import org.jspecify.annotations.Nullable;

/**
 * 声明式触发条件：决定 {@link ChoreographyPhrase} 何时展开为舞台事件。
 */
public sealed interface TriggerSpec
	permits TriggerSpec.OnFeature, TriggerSpec.EveryNBeats, TriggerSpec.FirstFeature {

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

	/** 每第 N 次匹配的 feature 事件触发一次（例如 kick every 4 beats）。 */
	record EveryNBeats(
		int interval,
		String anchorFeatureKey
	) implements TriggerSpec {
		public EveryNBeats {
			interval = Math.max(1, interval);
			anchorFeatureKey = anchorFeatureKey != null ? anchorFeatureKey : "kick";
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

	static TriggerSpec fromValue(@Nullable String type, @Nullable String featureKey, int interval, float minEnergy) {
		if ("every_n_beats".equalsIgnoreCase(type) || "everyNBeats".equalsIgnoreCase(type)) {
			return new EveryNBeats(interval > 0 ? interval : 4, featureKey != null ? featureKey : "kick");
		}
		if ("first_feature".equalsIgnoreCase(type) || "firstFeature".equalsIgnoreCase(type)) {
			return new FirstFeature(featureKey != null ? featureKey : "kick", minEnergy);
		}
		return new OnFeature(featureKey != null ? featureKey : "low", minEnergy);
	}
}
