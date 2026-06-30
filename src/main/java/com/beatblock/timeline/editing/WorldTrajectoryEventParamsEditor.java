package com.beatblock.timeline.editing;

import com.beatblock.timeline.generation.RhythmDropEventFactory;
import com.beatblock.ui.util.UiNumberFormatter;
import java.util.Map;

/**
 * {@link com.beatblock.engine.influence.BlockInfluencePresets} 中 WORLD_TRAJECTORY 类动画
 * （Meteor / RhythmDrop）的事件参数字段读写与校验。
 */
public final class WorldTrajectoryEventParamsEditor {

	public static final String METEOR_ANIMATION_ID = "Meteor";
	public static final String RHYTHM_DROP_ANIMATION_ID = RhythmDropEventFactory.RHYTHM_DROP_ANIMATION_TYPE_ID;

	private WorldTrajectoryEventParamsEditor() {}

	public sealed interface MergeResult {
		record Ok(Map<String, Object> parameters) implements MergeResult {}
		record Err(String message) implements MergeResult {}
	}

	public record FormInput(
		String singleBlockX,
		String singleBlockY,
		String singleBlockZ,
		String meteorHeight,
		String meteorScatter,
		String impactThreshold
	) {}

	public static boolean supports(String animationId) {
		if (animationId == null || animationId.isBlank()) return false;
		return METEOR_ANIMATION_ID.equalsIgnoreCase(animationId.trim())
			|| RHYTHM_DROP_ANIMATION_ID.equalsIgnoreCase(animationId.trim());
	}

	public static void clear(Map<String, Object> parameters) {
		if (parameters == null) return;
		parameters.remove("singleBlockX");
		parameters.remove("singleBlockY");
		parameters.remove("singleBlockZ");
		parameters.remove("meteorHeight");
		parameters.remove("meteorScatter");
		parameters.remove("impactThreshold");
		parameters.remove("impactVfxKind");
	}

	public static FormInput readForm(Map<String, Object> params, String animationId) {
		Map<String, Object> safe = params != null ? params : Map.of();
		boolean rhythmDrop = RHYTHM_DROP_ANIMATION_ID.equalsIgnoreCase(String.valueOf(animationId).trim());
		double defaultHeight = rhythmDrop ? RhythmDropEventFactory.DEFAULT_FALL_HEIGHT_BLOCKS : 12.0;
		double defaultScatter = rhythmDrop ? 0.0 : 2.5;
		double defaultImpact = 0.92;

		return new FormInput(
			readIntString(safe, "singleBlockX"),
			readIntString(safe, "singleBlockY"),
			readIntString(safe, "singleBlockZ"),
			UiNumberFormatter.format(readDouble(safe, "meteorHeight", defaultHeight)),
			UiNumberFormatter.format(readDouble(safe, "meteorScatter", defaultScatter)),
			UiNumberFormatter.format(readDouble(safe, "impactThreshold", defaultImpact))
		);
	}

	public static MergeResult merge(Map<String, Object> parameters, String animationId, FormInput input) {
		if (parameters == null) {
			return new MergeResult.Err("无效参数。");
		}
		if (!supports(animationId)) {
			return new MergeResult.Ok(parameters);
		}
		if (input == null) {
			return new MergeResult.Ok(parameters);
		}

		boolean rhythmDrop = RHYTHM_DROP_ANIMATION_ID.equalsIgnoreCase(animationId.trim());

		try {
			double height = parseRequiredDouble(input.meteorHeight(), "下落高度");
			if (height <= 0) {
				return new MergeResult.Err("下落高度必须大于 0。");
			}
			parameters.put("meteorHeight", height);

			if (rhythmDrop) {
				parameters.put("meteorScatter", 0.0);
				double threshold = parseRequiredDouble(input.impactThreshold(), "命中阈值");
				if (threshold < 0.0 || threshold > 1.0) {
					return new MergeResult.Err("命中阈值须在 0 到 1 之间。");
				}
				parameters.put("impactThreshold", threshold);
				parameters.put("impactVfxKind", "rhythm_impact");
			} else {
				double scatter = parseRequiredDouble(input.meteorScatter(), "横向散射");
				if (scatter < 0) {
					return new MergeResult.Err("横向散射不能为负数。");
				}
				parameters.put("meteorScatter", scatter);
				parameters.remove("impactThreshold");
				parameters.remove("impactVfxKind");
			}

			MergeResult blockResult = mergeSingleBlock(parameters, input);
			if (blockResult instanceof MergeResult.Err err) {
				return err;
			}
			return new MergeResult.Ok(parameters);
		} catch (NumberFormatException ex) {
			return new MergeResult.Err("轨迹参数格式不正确。");
		}
	}

	private static MergeResult mergeSingleBlock(Map<String, Object> parameters, FormInput input) {
		String xRaw = trim(input.singleBlockX());
		String yRaw = trim(input.singleBlockY());
		String zRaw = trim(input.singleBlockZ());
		boolean any = !xRaw.isEmpty() || !yRaw.isEmpty() || !zRaw.isEmpty();
		if (!any) {
			parameters.remove("singleBlockX");
			parameters.remove("singleBlockY");
			parameters.remove("singleBlockZ");
			return new MergeResult.Ok(parameters);
		}
		if (xRaw.isEmpty() || yRaw.isEmpty() || zRaw.isEmpty()) {
			return new MergeResult.Err("精确落点需同时填写 X、Y、Z，或全部留空以使用目标对象方块。");
		}
		parameters.put("singleBlockX", Integer.parseInt(xRaw));
		parameters.put("singleBlockY", Integer.parseInt(yRaw));
		parameters.put("singleBlockZ", Integer.parseInt(zRaw));
		return new MergeResult.Ok(parameters);
	}

	private static String readIntString(Map<String, Object> params, String key) {
		if (!params.containsKey(key)) return "";
		Object raw = params.get(key);
		if (raw == null) return "";
		return String.valueOf(raw).trim();
	}

	private static double readDouble(Map<String, Object> params, String key, double fallback) {
		Object raw = params.get(key);
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private static double parseRequiredDouble(String raw, String label) {
		String trimmed = trim(raw);
		if (trimmed.isEmpty()) {
			throw new NumberFormatException(label);
		}
		return Double.parseDouble(trimmed);
	}

	private static String trim(String raw) {
		return raw != null ? raw.trim() : "";
	}
}
