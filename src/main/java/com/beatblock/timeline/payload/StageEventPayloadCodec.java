package com.beatblock.timeline.payload;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link StageEventPayload} ↔ 历史参数 Map 编解码。
 * <p>
 * 键名与 {@code AnimationEventPropertiesEditor} / 播放器既有契约保持一致，保证 .osc 往返。
 */
public final class StageEventPayloadCodec {

	/**
	 * 已被强类型建模的键；解码时不会落入 extensions。
	 * 含 actionMode/mode 等核心键与各变体字段。
	 */
	public static final Set<String> KNOWN_KEYS = Set.of(
		// core
		"actionMode", "mode", "animationType", "targetObject", "energy", "durationSeconds", "eventOrigin",
		"energyThreshold",
		// animate
		"dispatchModel", "inheritGroupSpatial", "spatialMode", "sequentialDelaySeconds",
		"pacingMode", "distancePaceSecondsPerBlock", "distancePaceMinGapSeconds", "blocksPerBeat",
		"stepStartMode", "stepCompletionMode", "cameraAdaptiveStep", "cameraFrustumGating", "cameraEdgePriority",
		"usePhaseAnimation", "entryDurationPercent", "idleDurationPercent", "exitDurationPercent",
		"cameraNearDistance", "cameraFarDistance", "cameraNearScale", "cameraFarScale",
		"vfxEnabled", "flashBlock", "flashBlockId",
		"singleBlockX", "singleBlockY", "singleBlockZ",
		// build / place
		"buildMode", "buildDissolve", "layerId", "placeBlock", "placeBlockId"
	);

	private StageEventPayloadCodec() {}

	public static @NonNull StageEventPayload fromAnimationEvent(@NonNull TimelineAnimationEvent event) {
		Map<String, Object> source = new HashMap<>(event.getParameters());
		// 顶层字段优先于 map 内可能过期的副本
		source.put("animationType", event.getAnimationTypeId());
		source.put("targetObject", event.getTargetObjectId());
		source.put("energy", event.getEnergy());
		source.put("durationSeconds", event.getDurationSeconds());
		if (!source.containsKey("eventOrigin") && !source.containsKey("actionMode") && !source.containsKey("mode")) {
			// 保持 decode 逻辑完整
		}
		return decode(source);
	}

	public static @NonNull StageEventPayload decode(@Nullable Map<String, Object> params) {
		Map<String, Object> source = params != null ? params : Map.of();
		TimelineAnimationActionMode mode = readActionMode(source);
		String animationType = ParamValues.string(source, "animationType", "");
		String targetObject = ParamValues.string(source, "targetObject", "");
		float energy = ParamValues.floatValue(source, "energy", 1f);
		double duration = ParamValues.number(source, "durationSeconds", 0.01);
		TimelineEventOrigin origin = TimelineEventOrigin.fromValue(source.get("eventOrigin"));
		float energyThreshold = ParamValues.floatValue(source, "energyThreshold", 0f);
		Map<String, Object> extensions = extractExtensions(source);

		return switch (mode) {
			case BUILD -> new StageEventPayload.Build(
				animationType,
				targetObject,
				energy,
				duration,
				origin,
				energyThreshold,
				ParamValues.string(source, "buildMode", "wall"),
				ParamValues.bool(source, "buildDissolve", false),
				firstNonBlank(
					ParamValues.string(source, "placeBlock", ""),
					ParamValues.string(source, "placeBlockId", "")
				),
				emptyToNull(ParamValues.string(source, "layerId", "")),
				extensions
			);
			case PLACE -> new StageEventPayload.Place(
				animationType,
				targetObject,
				energy,
				duration,
				origin,
				energyThreshold,
				firstNonBlank(
					ParamValues.string(source, "placeBlock", ""),
					ParamValues.string(source, "placeBlockId", ""),
					"minecraft:diamond_block"
				),
				extensions
			);
			case CLEAR -> new StageEventPayload.Clear(
				animationType,
				targetObject,
				energy,
				duration,
				origin,
				energyThreshold,
				extensions
			);
			case ANIMATE -> new StageEventPayload.Animate(
				animationType,
				targetObject,
				energy,
				duration,
				origin,
				energyThreshold,
				DispatchModel.fromValue(source.get("dispatchModel")),
				SpatialParams.fromMap(source),
				StepParams.fromMap(source),
				emptyToNull(firstNonBlank(
					ParamValues.string(source, "flashBlock", ""),
					ParamValues.string(source, "flashBlockId", "")
				)),
				ParamValues.bool(source, "vfxEnabled", true),
				SingleBlockRef.fromMap(source).orElse(null),
				extensions
			);
		};
	}

	public static @NonNull Map<String, Object> encode(@NonNull StageEventPayload payload) {
		Map<String, Object> map = new HashMap<>();
		if (!payload.extensions().isEmpty()) {
			map.putAll(payload.extensions());
		}
		writeCore(map, payload);

		if (payload instanceof StageEventPayload.Animate a) {
			map.put("dispatchModel", a.dispatchModel().name());
			a.spatial().writeInto(map);
			if (a.dispatchModel() == DispatchModel.STEP) {
				a.step().writeInto(map);
			}
			map.put("vfxEnabled", a.vfxEnabled());
			if (a.flashBlockId() != null) {
				map.put("flashBlock", a.flashBlockId());
			}
			SingleBlockRef singleBlock = a.singleBlock();
			if (singleBlock != null) {
				singleBlock.writeInto(map);
			}
		} else if (payload instanceof StageEventPayload.Build b) {
			map.put("buildMode", b.buildMode());
			if (b.dissolve()) {
				map.put("buildDissolve", "true");
			}
			if (b.placeBlockId() != null) {
				map.put("placeBlock", b.placeBlockId());
			}
			if (b.layerId() != null) {
				map.put("layerId", b.layerId());
			}
		} else if (payload instanceof StageEventPayload.Place p) {
			map.put("placeBlock", p.placeBlockId());
		}
		// CLEAR: 仅核心字段

		return map;
	}

	private static void writeCore(Map<String, Object> map, StageEventPayload payload) {
		String mode = payload.actionMode().name();
		map.put("actionMode", mode);
		map.put("mode", mode);
		map.put("animationType", payload.animationType());
		map.put("targetObject", payload.targetObject());
		map.put("energy", payload.energy());
		map.put("durationSeconds", payload.durationSeconds());
		map.put("eventOrigin", payload.eventOrigin().name());
		// 始终写出，便于属性面板与 .osc 往返（缺省 0 表示无门槛）
		map.put("energyThreshold", payload.energyThreshold());
	}

	private static TimelineAnimationActionMode readActionMode(Map<String, Object> source) {
		Object modeRaw = source.get("actionMode");
		if (modeRaw == null) modeRaw = source.get("mode");
		return TimelineAnimationActionMode.fromValue(modeRaw);
	}

	private static Map<String, Object> extractExtensions(Map<String, Object> source) {
		Map<String, Object> ext = new HashMap<>();
		for (Map.Entry<String, Object> e : source.entrySet()) {
			if (e.getKey() == null) continue;
			if (KNOWN_KEYS.contains(e.getKey())) continue;
			ext.put(e.getKey(), e.getValue());
		}
		return ext.isEmpty() ? Map.of() : Map.copyOf(ext);
	}

	private static @Nullable String emptyToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	private static String firstNonBlank(String... values) {
		if (values == null) return "";
		for (String v : values) {
			if (v != null && !v.isBlank()) return v;
		}
		return "";
	}

	/** 已知键集合的可变副本（测试用）。 */
	static Set<String> knownKeysCopy() {
		return new HashSet<>(KNOWN_KEYS);
	}
}
