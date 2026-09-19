package com.beatblock.automap.vfx;

import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.cast.StageCast;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.ChoreographyVfxFactory;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.ParticleDirector;
import com.beatblock.automap.engine.ParticleEvent;
import com.beatblock.automap.engine.ParticleType;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.performance.LayerIntensity;
import com.beatblock.automap.performance.PerformanceProfile;
import com.beatblock.automap.performance.SectionActivity;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VFX 导演：Accent / Section Edge / Hero / Environment，而非纯高频峰值检测。
 * <p>
 * Accent 仍复用 {@link ParticleDirector} 峰值，但按 {@link SectionActivity} 缩放密度。
 */
public final class VfxPlanner {

	private static final double ACCENT_BASE_GAP = 0.15;

	private VfxPlanner() {}

	/**
	 * 用导演结果替换计划中的 VFX 车道。
	 * {@code settings.isParticlesEnabled()==false} 或 VFX intensity OFF → 清空。
	 */
	public static ChoreographyPlan apply(
		@Nullable ChoreographyPlan plan,
		@Nullable List<FrequencyBands> bands,
		@Nullable AutoMapSettings settings
	) {
		if (plan == null) {
			return ChoreographyPlan.empty();
		}
		if (settings == null || !settings.isParticlesEnabled()) {
			return withVfx(plan, List.of());
		}
		PerformanceProfile profile = settings.getPerformanceProfile();
		if (profile != null && (profile.wantsBuildPrimary() || !profile.wantsVfx())) {
			return withVfx(plan, List.of());
		}
		LayerIntensity intensity = profile != null ? profile.vfx() : LayerIntensity.MEDIUM;
		if (!intensity.isEnabled()) {
			return withVfx(plan, List.of());
		}

		List<String> targets = settings.getTargetObjectIds();
		StageCast cast = StageCast.fromTargetIds(targets);
		List<ChoreographyVfx> out = new ArrayList<>();
		out.addAll(sectionEdgeVfx(plan, intensity));
		out.addAll(heroVfx(plan, cast, intensity));
		out.addAll(environmentVfx(plan, intensity));
		if (intensity.keepRatio() >= 0.3) {
			out.addAll(accentVfx(plan, bands, targets, intensity));
		}
		out.sort(Comparator.comparingDouble(ChoreographyVfx::timeSeconds));
		return withVfx(plan, out);
	}

	static List<ChoreographyVfx> sectionEdgeVfx(ChoreographyPlan plan, LayerIntensity intensity) {
		if (intensity.keepRatio() < 0.1) {
			return List.of();
		}
		List<ChoreographyVfx> out = new ArrayList<>();
		List<ChoreographyPlan.SectionPlan> sections = plan.sections();
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			SectionType type = section.sectionType();
			if (type == SectionType.DROP || type == SectionType.CHORUS) {
				out.add(new ChoreographyVfx.ScreenFlash(
					section.startSeconds(),
					type == SectionType.DROP ? "drop_entrance" : "chorus_entrance",
					1f, 1f, 1f,
					type == SectionType.DROP ? 0.18 : 0.12,
					i
				));
			}
		}
		return out;
	}

	static List<ChoreographyVfx> heroVfx(
		ChoreographyPlan plan,
		StageCast cast,
		LayerIntensity intensity
	) {
		if (intensity.keepRatio() < 0.2) {
			return List.of();
		}
		CameraSubject subject = resolveHeroSubject(cast);
		List<ChoreographyVfx> out = new ArrayList<>();
		for (ChoreographyPhrase phrase : plan.choreographyPhrases()) {
			if (phrase == null || !phrase.isHero()) {
				continue;
			}
			int index = phrase.sectionIndex();
			if (index < 0 || index >= plan.sections().size()) {
				continue;
			}
			ChoreographyPlan.SectionPlan section = plan.sections().get(index);
			out.add(new ChoreographyVfx.ParticleBurst(
				section.startSeconds(),
				"hero_burst",
				"minecraft:firework",
				subject,
				24,
				0.7,
				0.06,
				index
			));
		}
		return out;
	}

	static List<ChoreographyVfx> environmentVfx(ChoreographyPlan plan, LayerIntensity intensity) {
		if (intensity.keepRatio() < 0.35) {
			return List.of();
		}
		List<ChoreographyVfx> out = new ArrayList<>();
		List<ChoreographyPlan.SectionPlan> sections = plan.sections();
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			SectionType type = section.sectionType();
			if (type == SectionType.BREAK || type == SectionType.BRIDGE) {
				out.add(new ChoreographyVfx.EnvironmentLighting(
					section.startSeconds(),
					"break_mood",
					0.75,
					0.55f, 0.65f, 0.95f,
					Math.min(2.0, section.durationSeconds() * 0.25),
					i
				));
			} else if (type == SectionType.OUTRO) {
				out.add(new ChoreographyVfx.ScreenTint(
					section.startSeconds(),
					"outro_fade",
					0.45,
					0.15f, 0.12f, 0.28f,
					Math.max(1.0, section.durationSeconds()),
					i
				));
			}
		}
		return out;
	}

	static List<ChoreographyVfx> accentVfx(
		ChoreographyPlan plan,
		@Nullable List<FrequencyBands> bands,
		List<String> targets,
		LayerIntensity intensity
	) {
		List<ParticleEvent> peaks = ParticleDirector.generate(bands, true);
		if (peaks.isEmpty()) {
			return List.of();
		}
		double keepRatio = Math.max(0.15, intensity.keepRatio());
		Map<String, Double> lastByBand = new HashMap<>();
		List<ChoreographyVfx> candidates = new ArrayList<>();
		for (ParticleEvent event : peaks) {
			SectionType type = sectionTypeAt(plan, event.getTimeSeconds());
			double sectionAct = SectionActivity.forSectionType(type);
			double activity = SectionActivity.effective(sectionAct, 1.0) * keepRatio;
			activity = Math.max(0.15, Math.min(1.2, activity));
			double minGap = ACCENT_BASE_GAP / activity;
			String band = event.getType().name();
			double last = lastByBand.getOrDefault(band, -minGap - 1);
			if (event.getTimeSeconds() < last + minGap) {
				continue;
			}
			// sparse sections: drop weaker Dust unless KEEP is high
			if (sectionAct < 0.45 && event.getType() == ParticleType.DUST && keepRatio < 0.9) {
				continue;
			}
			int sectionIndex = sectionIndexAt(plan, event.getTimeSeconds());
			candidates.add(ChoreographyVfxFactory.fromParticleEvent(event, sectionIndex, targets));
			lastByBand.put(band, event.getTimeSeconds());
		}
		return candidates;
	}

	private static ChoreographyPlan withVfx(ChoreographyPlan plan, List<ChoreographyVfx> vfx) {
		return new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			plan.motionPhrases(),
			plan.cameraPhrases(),
			vfx,
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			plan.spatialMotifPhrases(),
			plan.choreographyPhrases(),
			plan.buildSequences()
		);
	}

	private static CameraSubject resolveHeroSubject(@Nullable StageCast cast) {
		if (cast != null) {
			String heroId = cast.primaryHeroId();
			if (heroId != null && !heroId.isBlank()) {
				return CameraSubject.stageObject(heroId);
			}
			List<String> focusable = cast.choreographyParticipantIds();
			if (!focusable.isEmpty()) {
				return CameraSubject.stageObject(focusable.getFirst());
			}
		}
		return CameraSubject.allStageObjects();
	}

	private static CameraSubject resolveTarget(List<String> targets) {
		StageCast cast = StageCast.fromTargetIds(targets);
		return resolveHeroSubject(cast);
	}

	private static SectionType sectionTypeAt(ChoreographyPlan plan, double timeSeconds) {
		int index = sectionIndexAt(plan, timeSeconds);
		if (index < 0 || index >= plan.sections().size()) {
			return SectionType.VERSE;
		}
		return plan.sections().get(index).sectionType();
	}

	private static int sectionIndexAt(ChoreographyPlan plan, double timeSeconds) {
		List<ChoreographyPlan.SectionPlan> sections = plan.sections();
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			boolean withinEnd = i == sections.size() - 1
				? timeSeconds <= section.endSeconds()
				: timeSeconds < section.endSeconds();
			if (timeSeconds >= section.startSeconds() && withinEnd) {
				return i;
			}
		}
		return -1;
	}
}
