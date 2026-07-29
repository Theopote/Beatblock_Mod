package com.beatblock.timeline.payload;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * 烘焙 STEP / RhythmDrop 产生的单块绝对坐标（singleBlockX/Y/Z）。
 */
public record SingleBlockRef(int x, int y, int z) {

	public static @NonNull Optional<SingleBlockRef> fromMap(@Nullable Map<String, Object> map) {
		if (map == null) return Optional.empty();
		if (!map.containsKey("singleBlockX") || !map.containsKey("singleBlockY") || !map.containsKey("singleBlockZ")) {
			return Optional.empty();
		}
		return Optional.of(new SingleBlockRef(
			ParamValues.intValue(map, "singleBlockX", 0),
			ParamValues.intValue(map, "singleBlockY", 0),
			ParamValues.intValue(map, "singleBlockZ", 0)
		));
	}

	public void writeInto(@NonNull Map<String, Object> target) {
		target.put("singleBlockX", x);
		target.put("singleBlockY", y);
		target.put("singleBlockZ", z);
	}
}
