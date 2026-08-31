package com.beatblock.ui.presenter;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.i18n.BBTexts;

import java.util.List;
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

	public List<StageTargetOption> listStageTargets() {
		var engine = context.get().blockAnimationEngine();
		StageObjectSystem system = engine != null ? engine.getStageObjectSystem() : null;
		return StageTargetOption.fromSystem(system);
	}

	public void applyDefaultTargets(AutoMapSettings settings) {
		if (settings == null) return;
		if (!settings.getTargetObjectIds().isEmpty()) return;
		settings.setTargetObjectIds(StageTargetOption.defaultTargetIds(listStageTargets()));
	}

	public String generateBlockedReason() {
		if (lastFeatureTimeline() == null) {
			return BBTexts.get("beatblock.message.import_music_first");
		}
		if (timeline() == null) {
			return BBTexts.get("beatblock.message.timeline_unavailable");
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
