package com.beatblock.timeline;

import com.beatblock.ui.i18n.BBTexts;

/**
 * Marker 类型职责（架构契约，勿打破）。
 *
 * <pre>
 * SECTION
 *   = structural / navigation marker
 *     may participate in section lookup / binding
 *
 * GENERIC / DROP / CAMERA / FX
 *   = authoring / navigation markers only
 *     never execute runtime behavior
 * </pre>
 *
 * <p><b>反模式（禁止）：</b>
 * {@code if (marker.getType() == CAMERA) triggerCamera(...)} —
 * Camera / VFX / Animation 执行只走各自 Track，不经 Marker。
 */
public enum MarkerType {
	/** Authoring / navigation only. Never executes runtime behavior. */
	GENERIC("普通", 0xEE_FF_D4_66),

	/**
	 * Structural / navigation marker (projection of Music Structure, not the sole SoT).
	 * May participate in section lookup / Animation Binding section filter.
	 * Edits should project onto {@code ChoreographyPlan} via SectionMarkerStructureBridge
	 * to avoid silent drift; does not itself execute animations.
	 */
	SECTION("段落", 0xEE_66_DD_FF),

	/** Authoring / navigation only. Never executes runtime behavior. */
	DROP("Drop", 0xEE_66_FF_88),

	/**
	 * Authoring / navigation cue for camera beats.
	 * Never triggers Camera Track / CameraShot execution.
	 */
	CAMERA("镜头", 0xEE_FF_99_66),

	/**
	 * Authoring / navigation cue for VFX beats.
	 * Never triggers Global / VFX Track execution.
	 */
	FX("特效", 0xEE_D2_88_FF);

	private final String displayName;
	private final int colorAbgr;

	MarkerType(String displayName, int colorAbgr) {
		this.displayName = displayName;
		this.colorAbgr = colorAbgr;
	}

	/**
	 * {@code true} only for {@link #SECTION}.
	 * Structural markers may participate in section lookup / binding — still not runtime executors.
	 */
	public boolean isStructural() {
		return this == SECTION;
	}

	/**
	 * {@code true} for GENERIC / DROP / CAMERA / FX.
	 * Authoring / navigation only; never execute runtime behavior.
	 */
	public boolean isAnnotation() {
		return !isStructural();
	}

	public String getDisplayName() {
		return BBTexts.get(switch (this) {
			case GENERIC -> "beatblock.marker_type.generic";
			case SECTION -> "beatblock.marker_type.section";
			case DROP -> "beatblock.marker_type.drop";
			case CAMERA -> "beatblock.marker_type.camera";
			case FX -> "beatblock.marker_type.fx";
		});
	}

	public int getColorAbgr() {
		return colorAbgr;
	}

	public static MarkerType fromName(String name) {
		if (name == null || name.isBlank()) return GENERIC;
		for (MarkerType type : values()) {
			if (type.name().equalsIgnoreCase(name.trim())) return type;
		}
		return GENERIC;
	}

	public static String[] displayNames() {
		return BBTexts.labels(
			"beatblock.marker_type.generic",
			"beatblock.marker_type.section",
			"beatblock.marker_type.drop",
			"beatblock.marker_type.camera",
			"beatblock.marker_type.fx"
		);
	}
}
