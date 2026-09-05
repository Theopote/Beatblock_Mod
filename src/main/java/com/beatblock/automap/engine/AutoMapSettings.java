package com.beatblock.automap.engine;

import com.beatblock.automap.choreography.ChoreographyLayerProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart Auto-Map 弹窗配置：风格、复杂度、镜头/粒子开关、编舞层档位、目标对象与可选 per-feature minGap。
 */
public final class AutoMapSettings {

	private AutoMapStyle style;
	private Complexity complexity;
	private boolean cameraEnabled;
	private boolean particlesEnabled;
	private ChoreographyLayerProfile layerProfile;
	private List<String> targetObjectIds;
	private double minGapLow;
	private double minGapMid;
	private double minGapHigh;

	public AutoMapSettings() {
		this.style = AutoMapStyle.EDM;
		this.complexity = Complexity.MEDIUM;
		this.cameraEnabled = true;
		this.particlesEnabled = true;
		this.layerProfile = ChoreographyLayerProfile.HERO_FULL;
		this.targetObjectIds = new ArrayList<>();
		this.minGapLow = 0.0;
		this.minGapMid = 0.0;
		this.minGapHigh = 0.0;
	}

	public AutoMapStyle getStyle() { return style; }
	public void setStyle(AutoMapStyle style) { this.style = style != null ? style : AutoMapStyle.EDM; }

	public Complexity getComplexity() { return complexity; }
	public void setComplexity(Complexity complexity) { this.complexity = complexity != null ? complexity : Complexity.MEDIUM; }

	public boolean isCameraEnabled() { return cameraEnabled; }
	public void setCameraEnabled(boolean cameraEnabled) { this.cameraEnabled = cameraEnabled; }

	public boolean isParticlesEnabled() { return particlesEnabled; }
	public void setParticlesEnabled(boolean particlesEnabled) { this.particlesEnabled = particlesEnabled; }

	public ChoreographyLayerProfile getLayerProfile() { return layerProfile; }
	public void setLayerProfile(ChoreographyLayerProfile layerProfile) {
		this.layerProfile = layerProfile != null ? layerProfile : ChoreographyLayerProfile.HERO_FULL;
	}

	public List<String> getTargetObjectIds() { return new ArrayList<>(targetObjectIds); }
	public void setTargetObjectIds(List<String> ids) { this.targetObjectIds = ids != null ? new ArrayList<>(ids) : new ArrayList<>(); }

	/** &lt;= 0 表示使用复杂度默认 per-feature minGap。 */
	public double getMinGapLow() { return minGapLow; }
	public void setMinGapLow(double minGapLow) { this.minGapLow = Math.max(0.0, minGapLow); }

	public double getMinGapMid() { return minGapMid; }
	public void setMinGapMid(double minGapMid) { this.minGapMid = Math.max(0.0, minGapMid); }

	public double getMinGapHigh() { return minGapHigh; }
	public void setMinGapHigh(double minGapHigh) { this.minGapHigh = Math.max(0.0, minGapHigh); }

	public double resolveMinGapLow(double defaultValue) {
		return minGapLow > 0 ? minGapLow : defaultValue;
	}

	public double resolveMinGapMid(double defaultValue) {
		return minGapMid > 0 ? minGapMid : defaultValue;
	}

	public double resolveMinGapHigh(double defaultValue) {
		return minGapHigh > 0 ? minGapHigh : defaultValue;
	}
}
