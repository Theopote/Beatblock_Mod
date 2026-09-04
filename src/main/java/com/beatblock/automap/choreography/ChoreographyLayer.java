package com.beatblock.automap.choreography;

import java.util.HashMap;
import java.util.Map;

/**
 * 编舞语义层：区分局部 accent、跨对象 phrase 与段落高潮 hero。
 * <ul>
 *   <li>{@link #ACCENT} — {@link ChoreographyPlan.MotionPhrase}：单个音乐事件的局部响应</li>
 *   <li>{@link #PHRASE} — {@link com.beatblock.automap.choreography.grammar.ChoreographyPhrase}：段内重复的跨对象编舞</li>
 *   <li>{@link #HERO} — 段落入口 / 高潮的一次性全强度编舞（见 {@link com.beatblock.automap.choreography.grammar.ChoreographyHeroSelection}）</li>
 * </ul>
 */
public enum ChoreographyLayer {
	ACCENT(0.25f),
	PHRASE(0.75f),
	HERO(1.0f);

	public static final String PARAM_KEY = "choreographyLayer";

	private final float defaultIntensityScale;

	ChoreographyLayer(float defaultIntensityScale) {
		this.defaultIntensityScale = Math.max(0f, Math.min(1f, defaultIntensityScale));
	}

	public float defaultIntensityScale() {
		return defaultIntensityScale;
	}

	public float scaleEnergy(float energy) {
		return Math.max(0f, Math.min(1f, energy * defaultIntensityScale));
	}

	/** Accent 默认局部原语：轻量 pulse，避免与 Phrase 层同强度抢戏。 */
	public static String defaultAccentPrimitiveId() {
		return "pulse";
	}

	public Map<String, Object> scaleEventParams(Map<String, Object> params, float sourceEnergy) {
		Map<String, Object> scaled = params != null ? new HashMap<>(params) : new HashMap<>();
		float scaledEnergy = scaleEnergy(sourceEnergy);
		scaled.put("energy", scaledEnergy);
		Object height = scaled.get("height");
		if (height instanceof Number number) {
			scaled.put("height", number.floatValue() * defaultIntensityScale);
		}
		scaled.put(PARAM_KEY, name());
		return scaled;
	}
}
