package com.beatblock.ui.presenter;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.MusicAnalysisAsset;
import com.beatblock.audio.analysis.MusicAnalysisAssetResolver;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.SmartAutoMapGenerateCommand;
import com.beatblock.timeline.generation.GenerationReapplyGuard;
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

	/** @deprecated 使用 {@link #resolveMusicAnalysis()}；保留供测试过渡读取。 */
	@Deprecated
	public AudioFeatureTimeline lastFeatureTimeline() {
		MusicAnalysisAsset asset = resolveMusicAnalysis();
		return asset != null ? asset.featureTimeline() : null;
	}

	public MusicAnalysisAsset resolveMusicAnalysis() {
		BeatBlockContext ctx = context.get();
		return MusicAnalysisAssetResolver.resolve(ctx.timeline(), ctx.audioAnalysisEngine());
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
		if (resolveMusicAnalysis() == null) {
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

	public boolean smartAutoMapRequiresConfirmation() {
		return GenerationReapplyGuard.requiresConfirmation(timeline(), GenerationReapplyGuard.Kind.SMART_AUTO_MAP);
	}

	public int smartAutoMapAffectedEventCount() {
		return GenerationReapplyGuard.affectedEventCount(timeline(), GenerationReapplyGuard.Kind.SMART_AUTO_MAP);
	}

	public GenerateOutcome generate(AutoMapSettings settings) {
		String blocked = generateBlockedReason();
		if (blocked != null) {
			return new GenerateOutcome(PresenterResult.failure(blocked), null);
		}
		MusicAnalysisAsset analysis = resolveMusicAnalysis();
		BeatBlockContext ctx = context.get();
		Timeline timeline = timeline();
		BuildLayerManager layers = null;
		var engine = ctx.blockAnimationEngine();
		if (engine != null) {
			layers = engine.getBuildLayerManager();
		}
		SmartAutoMapEngine.AutoMapResult result;
		CommandManager commands = ctx.commandManager();
		if (commands != null) {
			SmartAutoMapGenerateCommand command = SmartAutoMapGenerateCommand.runAndCapture(
				analysis.featureTimeline(), settings, timeline, layers);
			commands.execute(command);
			result = command.result();
		} else {
			result = SmartAutoMapEngine.generate(analysis.featureTimeline(), settings, timeline);
		}
		var editor = ctx.timelineEditor();
		if (editor != null) {
			editor.syncClockDuration();
		}
		return new GenerateOutcome(PresenterResult.success(""), result);
	}
}
