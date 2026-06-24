package com.beatblock.ui.presenter;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;

import java.util.function.Supplier;

/**
 * Smart Auto-Map 设置弹窗业务逻辑。
 */
public final class AutoMapSettingsPanelPresenter {

	public record GenerateOutcome(
		PresenterResult result,
		SmartAutoMapEngine.AutoMapResult autoMapResult
	) {}

	private final Supplier<BeatBlockContext> context;

	public AutoMapSettingsPanelPresenter(Supplier<BeatBlockContext> context) {
		this.context = context;
	}

	public AudioFeatureTimeline lastFeatureTimeline() {
		var engine = context.get().audioAnalysisEngine();
		return engine != null ? engine.getLastFeatureTimeline() : null;
	}

	public Timeline timeline() {
		return context.get().timeline();
	}

	public String generateBlockedReason() {
		if (lastFeatureTimeline() == null) {
			return "请先导入音乐以进行分析";
		}
		if (timeline() == null) {
			return "Timeline 不可用";
		}
		return null;
	}

	public boolean canGenerate() {
		return generateBlockedReason() == null;
	}

	public GenerateOutcome generate(AutoMapSettings settings) {
		String blocked = generateBlockedReason();
		if (blocked != null) {
			return new GenerateOutcome(PresenterResult.failure(blocked), null);
		}
		SmartAutoMapEngine.AutoMapResult result = SmartAutoMapEngine.generate(
			lastFeatureTimeline(), settings, timeline());
		var editor = context.get().timelineEditor();
		if (editor != null) {
			editor.syncClockDuration();
		}
		return new GenerateOutcome(PresenterResult.success(""), result);
	}
}
