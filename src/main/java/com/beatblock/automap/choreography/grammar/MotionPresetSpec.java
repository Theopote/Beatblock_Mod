package com.beatblock.automap.choreography.grammar;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** 局部运动原语（bounce / pulse 等），与空间 pattern 正交。 */
public record MotionPresetSpec(
	String presetId,
	double durationSeconds,
	boolean useEnergyForHeight,
	float heightMultiplier
) {
	public MotionPresetSpec {
		presetId = presetId != null && !presetId.isBlank() ? presetId : "pulse";
		durationSeconds = Math.max(0.01, durationSeconds);
		heightMultiplier = Math.max(0f, heightMultiplier);
	}

	public static MotionPresetSpec bounce() {
		return new MotionPresetSpec("bounce", 0.5, true, 4f);
	}

	public static MotionPresetSpec of(@Nullable String presetId) {
		return new MotionPresetSpec(presetId != null ? presetId : "pulse", 0.5, true, 4f);
	}

	/** 编舞语义 id → {@link com.beatblock.engine.AnimationLibrary} 注册 id。 */
	public String libraryAnimationTypeId() {
		return toLibraryAnimationTypeId(presetId);
	}

	public static String toLibraryAnimationTypeId(@Nullable String raw) {
		if (raw == null || raw.isBlank()) return "Pulse";
		String trimmed = raw.trim();
		return switch (trimmed.toLowerCase(Locale.ROOT)) {
			case "pulse", "bounce" -> "Pulse";
			case "jump", "eject" -> "BlockJump";
			case "rise" -> "BlockRise";
			case "drop", "fall" -> "BlockDrop";
			case "meteor" -> "Meteor";
			case "tap", "blocktap" -> "BlockTap";
			case "orbit", "slide" -> "Orbit";
			case "spiral", "spirallift" -> "SpiralLift";
			case "impact", "blockexplosion", "explosion" -> "BlockExplosion";
			case "rhythmdrop", "rhythm_drop" -> "RhythmDrop";
			default -> trimmed;
		};
	}
}
