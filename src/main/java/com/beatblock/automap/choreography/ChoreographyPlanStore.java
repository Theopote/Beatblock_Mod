package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.timeline.Timeline;

import org.jspecify.annotations.Nullable;

/**
 * 在 Timeline metadata 中持久化编舞计划与 AutoMap 配置，供 section 编辑 UI 使用。
 */
public final class ChoreographyPlanStore {

	public static final String KEY_PLAN = "beatblock.choreographyPlan";
	public static final String KEY_CONFIG = "beatblock.autoMapConfig";

	private ChoreographyPlanStore() {}

	public static void save(Timeline timeline, ChoreographyPlan plan, AutoMapConfig config) {
		if (timeline == null) return;
		if (plan != null) timeline.setMetadata(KEY_PLAN, plan);
		else timeline.setMetadata(KEY_PLAN, null);
		if (config != null) timeline.setMetadata(KEY_CONFIG, config);
		else timeline.setMetadata(KEY_CONFIG, null);
	}

	public static @Nullable ChoreographyPlan loadPlan(Timeline timeline) {
		if (timeline == null) return null;
		Object value = timeline.getMetadata(KEY_PLAN);
		return value instanceof ChoreographyPlan plan ? plan : null;
	}

	public static @Nullable AutoMapConfig loadConfig(Timeline timeline) {
		if (timeline == null) return null;
		Object value = timeline.getMetadata(KEY_CONFIG);
		return value instanceof AutoMapConfig config ? config : null;
	}

	public static boolean hasPlan(Timeline timeline) {
		return loadPlan(timeline) != null;
	}
}
