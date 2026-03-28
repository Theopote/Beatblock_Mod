package com.beatblock.engine;

import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.Map;

/**
 * 应用动画效果时的上下文，如舞台对象中心（用于爆炸、螺旋、环绕等）。
 * extraParams 携带绑定规则的额外参数（如 waveAmplitude、impactRadius 等），供各 Effect 读取覆盖默认值。
 */
public final class EffectContext {

	private final Vec3d stageCenter;
	private final Map<String, Object> extraParams;

	public EffectContext(Vec3d stageCenter) {
		this(stageCenter, Collections.emptyMap());
	}

	public EffectContext(Vec3d stageCenter, Map<String, Object> extraParams) {
		this.stageCenter = stageCenter != null ? stageCenter : Vec3d.ZERO;
		this.extraParams = extraParams != null ? extraParams : Collections.emptyMap();
	}

	public Vec3d getStageCenter() {
		return stageCenter;
	}

	public Map<String, Object> getExtraParams() {
		return extraParams;
	}

	/** 从 extraParams 读 double 值，若不存在或格式异常返回 fallback */
	public double paramDouble(String key, double fallback) {
		Object raw = extraParams.get(key);
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (Exception ex) {
			return fallback;
		}
	}
}
