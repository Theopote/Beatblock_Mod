package com.beatblock.automap.choreography.grammar;

/** 目标级微变化（高度交替等）。 */
public record VariationSpec(
	VariationKind kind,
	float amount
) {
	public VariationSpec {
		amount = Math.max(0f, amount);
		kind = kind != null ? kind : VariationKind.NONE;
	}

	public static VariationSpec none() {
		return new VariationSpec(VariationKind.NONE, 0f);
	}

	public static VariationSpec alternateHeight(float amount) {
		return new VariationSpec(VariationKind.ALTERNATE_HEIGHT, amount);
	}

	public float heightScaleForTargetIndex(int targetIndex) {
		if (kind != VariationKind.ALTERNATE_HEIGHT || amount <= 0f) {
			return 1f;
		}
		return targetIndex % 2 == 0 ? 1f : Math.max(0.1f, 1f - amount);
	}

	public enum VariationKind {
		NONE,
		ALTERNATE_HEIGHT
	}
}
