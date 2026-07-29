package com.beatblock.timeline.payload;

import com.beatblock.timeline.binding.SpatialDispatchMode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 空间调度：是否继承 GroupSpec、显式 spatialMode、逐步延迟。
 * {@code sequentialDelaySeconds < 0} 表示未显式指定（运行时按时长推导）。
 */
public record SpatialParams(
	boolean inheritGroupSpatial,
	@NonNull SpatialDispatchMode mode,
	double sequentialDelaySeconds
) {

	public static final SpatialParams DEFAULT = new SpatialParams(true, SpatialDispatchMode.ALL, -1.0);

	public SpatialParams {
		mode = mode != null ? mode : SpatialDispatchMode.ALL;
	}

	public boolean hasExplicitDelay() {
		return sequentialDelaySeconds >= 0.0;
	}

	public static @NonNull SpatialParams fromMap(@Nullable Map<String, Object> map) {
		boolean inherit = ParamValues.bool(map, "inheritGroupSpatial", true);
		SpatialDispatchMode mode = SpatialDispatchMode.fromValue(ParamValues.get(map, "spatialMode"));
		double delay = -1.0;
		if (map != null && map.containsKey("sequentialDelaySeconds")) {
			delay = Math.max(0.0, ParamValues.number(map, "sequentialDelaySeconds", 0.0));
		}
		return new SpatialParams(inherit, mode, delay);
	}

	public void writeInto(@NonNull Map<String, Object> target) {
		target.put("inheritGroupSpatial", inheritGroupSpatial);
		if (inheritGroupSpatial) {
			// 与 AnimationEventPropertiesEditor 一致：继承时不写 spatial 字段
			return;
		}
		target.put("spatialMode", mode.name());
		if (hasExplicitDelay()) {
			target.put("sequentialDelaySeconds", sequentialDelaySeconds);
		}
	}
}
