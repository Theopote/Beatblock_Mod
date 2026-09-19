package com.beatblock.automap.performance;

import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.engine.SectionType;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按 {@link PerformanceProfile} 整形编舞计划，拉开预设气质。
 */
public final class PerformancePlanShaper {

	private PerformancePlanShaper() {}

	public static ChoreographyPlan apply(@Nullable ChoreographyPlan plan, @Nullable PerformanceProfile profile) {
		if (plan == null) {
			return ChoreographyPlan.empty();
		}
		if (profile == null) {
			return plan;
		}

		List<ChoreographyPlan.MotionPhrase> motions = keepTopByEnergy(
			plan.motionPhrases(),
			profile.accent().keepRatio(),
			ChoreographyPlan.MotionPhrase::energy,
			ChoreographyPlan.MotionPhrase::timeSeconds
		);
		List<SpatialMotifPhrase> spatial = keepTopByEnergy(
			plan.spatialMotifPhrases(),
			profile.phrase().keepRatio(),
			SpatialMotifPhrase::energy,
			SpatialMotifPhrase::timeSeconds
		);
		List<ChoreographyPhrase> grammar = shapeGrammar(plan, profile);
		List<ChoreographyPlan.CameraPhrase> cameras = shapeCameras(plan.cameraPhrases(), profile.camera());
		List<ChoreographyVfx> vfx = shapeVfx(plan.vfxPhrases(), profile.vfx().keepRatio());
		DensityCurve density = reshapeDensity(plan.densityCurve(), plan.sections(), profile.density());

		if (profile.wantsBuildPrimary()) {
			// Build 主轴：动画 Accent/Phrase 再压一档，避免冒充 reveal
			motions = keepTopByEnergy(
				motions,
				Math.min(0.2, profile.accent().keepRatio()),
				ChoreographyPlan.MotionPhrase::energy,
				ChoreographyPlan.MotionPhrase::timeSeconds
			);
			spatial = List.of();
			grammar = profile.heroSectionEntrancesOnly()
				? keepSectionEntranceHeroes(plan, grammar)
				: List.of();
			vfx = shapeVfx(vfx, Math.min(0.2, profile.vfx().keepRatio()));
		}

		return new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			motions,
			cameras,
			vfx,
			density,
			plan.sectionEdits(),
			plan.musicalStructure(),
			spatial,
			grammar,
			plan.buildSequences()
		);
	}

	public static List<CameraShot> filterShots(List<CameraShot> shots, CameraTemperament temperament) {
		if (shots == null || shots.isEmpty()) {
			return List.of();
		}
		if (temperament == null || temperament == CameraTemperament.OFF) {
			return List.of();
		}
		List<CameraShot> out = new ArrayList<>(shots.size());
		for (CameraShot shot : shots) {
			if (shot == null) {
				continue;
			}
			CameraShotMovement movement = shot.movement();
			boolean keep = switch (temperament) {
				case OFF -> false;
				case ENERGETIC -> true;
				case BALANCED -> movement != CameraShotMovement.SHAKE
					|| shot.durationSeconds() >= 0.4;
				case SLOW_CINEMATIC -> movement == CameraShotMovement.HOLD
					|| movement == CameraShotMovement.PUSH_IN
					|| movement == CameraShotMovement.PULL_OUT
					|| movement == CameraShotMovement.PAN
					|| movement == CameraShotMovement.ORBIT;
			};
			if (keep) {
				out.add(shot);
			}
		}
		return List.copyOf(out);
	}

	private static List<ChoreographyPhrase> shapeGrammar(ChoreographyPlan plan, PerformanceProfile profile) {
		List<ChoreographyPhrase> phrases = plan.choreographyPhrases();
		if (phrases == null || phrases.isEmpty()) {
			return List.of();
		}
		if (profile.phrase().isOff() && profile.hero().isOff()) {
			return List.of();
		}
		List<ChoreographyPhrase> out = new ArrayList<>();
		for (ChoreographyPhrase phrase : phrases) {
			if (phrase == null) {
				continue;
			}
			if (phrase.isHero()) {
				if (profile.hero().isOff()) {
					continue;
				}
				if (profile.heroSectionEntrancesOnly()) {
					if (isSectionEntrance(plan, phrase)) {
						out.add(phrase);
					}
					continue;
				}
				if (profile.hero().keepRatio() >= 0.99 || out.stream().filter(ChoreographyPhrase::isHero).count()
					< Math.max(1, Math.round(countHeroes(phrases) * profile.hero().keepRatio()))) {
					out.add(phrase);
				}
				continue;
			}
			if (!profile.phrase().isOff() && profile.phrase().keepRatio() > 0) {
				out.add(phrase);
			}
		}
		if (!profile.phrase().isOff() && profile.phrase().keepRatio() < 0.99) {
			List<ChoreographyPhrase> nonHero = out.stream().filter(p -> !p.isHero()).toList();
			List<ChoreographyPhrase> heroes = out.stream().filter(ChoreographyPhrase::isHero).toList();
			int keep = Math.max(0, (int) Math.round(nonHero.size() * profile.phrase().keepRatio()));
			List<ChoreographyPhrase> trimmed = new ArrayList<>(nonHero.subList(0, Math.min(keep, nonHero.size())));
			trimmed.addAll(heroes);
			return List.copyOf(trimmed);
		}
		return List.copyOf(out);
	}

	private static List<ChoreographyPhrase> keepSectionEntranceHeroes(
		ChoreographyPlan plan,
		List<ChoreographyPhrase> grammar
	) {
		List<ChoreographyPhrase> out = new ArrayList<>();
		for (ChoreographyPhrase phrase : grammar) {
			if (phrase != null && phrase.isHero() && isSectionEntrance(plan, phrase)) {
				out.add(phrase);
			}
		}
		return List.copyOf(out);
	}

	private static boolean isSectionEntrance(ChoreographyPlan plan, ChoreographyPhrase phrase) {
		int index = phrase.sectionIndex();
		if (index < 0 || index >= plan.sections().size()) {
			return false;
		}
		ChoreographyPlan.SectionPlan section = plan.sections().get(index);
		SectionType type = section.sectionType();
		return type == SectionType.DROP
			|| type == SectionType.CHORUS
			|| type == SectionType.BUILD
			|| type == SectionType.INTRO;
	}

	private static long countHeroes(List<ChoreographyPhrase> phrases) {
		long n = 0;
		for (ChoreographyPhrase phrase : phrases) {
			if (phrase != null && phrase.isHero()) {
				n++;
			}
		}
		return n;
	}

	private static List<ChoreographyPlan.CameraPhrase> shapeCameras(
		List<ChoreographyPlan.CameraPhrase> cameras,
		CameraTemperament temperament
	) {
		if (cameras == null || cameras.isEmpty() || temperament == null || temperament == CameraTemperament.OFF) {
			return List.of();
		}
		if (temperament == CameraTemperament.ENERGETIC) {
			return cameras;
		}
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>();
		for (ChoreographyPlan.CameraPhrase camera : cameras) {
			if (camera == null) {
				continue;
			}
			String movement = camera.movement() != null ? camera.movement().toUpperCase() : "";
			boolean shake = movement.contains("SHAKE");
			if (temperament == CameraTemperament.BALANCED && shake) {
				continue;
			}
			if (temperament == CameraTemperament.SLOW_CINEMATIC) {
				if (shake) {
					continue;
				}
				if (!(movement.contains("HOLD")
					|| movement.contains("PUSH")
					|| movement.contains("PULL")
					|| movement.contains("PAN")
					|| movement.contains("ORBIT")
					|| movement.isBlank())) {
					continue;
				}
			}
			out.add(camera);
		}
		return List.copyOf(out);
	}

	private static DensityCurve reshapeDensity(
		DensityCurve curve,
		List<ChoreographyPlan.SectionPlan> sections,
		EventDensityPolicy policy
	) {
		if (curve == null) {
			curve = DensityCurve.uniform(0.5);
		}
		EventDensityPolicy safe = policy != null ? policy : EventDensityPolicy.SECTION_AWARE;
		return switch (safe) {
			case BEAT_HEAVY -> scaleCurve(curve, 1.15, 0.55, 1.0);
			case SECTION_AWARE -> curve;
			case SPARSE_CINEMATIC -> {
				if (sections == null || sections.isEmpty()) {
					yield scaleCurve(curve, 0.55, 0.15, 0.55);
				}
				List<DensityCurve.Point> points = new ArrayList<>();
				for (ChoreographyPlan.SectionPlan section : sections) {
					double base = curve.sampleAt((section.startSeconds() + section.endSeconds()) * 0.5);
					double scaled = switch (section.sectionType()) {
						case BUILD, DROP, CHORUS -> Math.min(0.75, base * 0.85);
						case INTRO, OUTRO, BREAK -> Math.min(0.35, base * 0.45);
						default -> Math.min(0.5, base * 0.6);
					};
					points.add(new DensityCurve.Point(section.startSeconds(), scaled));
				}
				yield DensityCurve.ofPoints(points);
			}
		};
	}

	private static DensityCurve scaleCurve(DensityCurve curve, double factor, double min, double max) {
		List<DensityCurve.Point> points = new ArrayList<>();
		for (DensityCurve.Point point : curve.points()) {
			double d = Math.max(min, Math.min(max, point.density() * factor));
			points.add(new DensityCurve.Point(point.timeSeconds(), d));
		}
		return points.isEmpty() ? DensityCurve.uniform(Math.max(min, Math.min(max, 0.5 * factor))) : DensityCurve.ofPoints(points);
	}

	private static <T> List<T> keepTopByEnergy(
		List<T> items,
		double keepRatio,
		java.util.function.ToDoubleFunction<T> energyFn,
		java.util.function.ToDoubleFunction<T> timeFn
	) {
		if (items == null || items.isEmpty() || keepRatio <= 0) {
			return List.of();
		}
		if (keepRatio >= 0.999) {
			return items;
		}
		List<T> sorted = new ArrayList<>(items);
		sorted.sort(Comparator
			.comparingDouble(energyFn).reversed()
			.thenComparingDouble(timeFn));
		int keep = Math.max(1, (int) Math.round(sorted.size() * keepRatio));
		keep = Math.min(keep, sorted.size());
		List<T> head = new ArrayList<>(sorted.subList(0, keep));
		head.sort(Comparator.comparingDouble(timeFn));
		return List.copyOf(head);
	}

	private static List<ChoreographyVfx> shapeVfx(List<ChoreographyVfx> vfx, double keepRatio) {
		if (vfx == null || vfx.isEmpty() || keepRatio <= 0) {
			return List.of();
		}
		List<ChoreographyVfx> structural = new ArrayList<>();
		List<ChoreographyVfx> accents = new ArrayList<>();
		for (ChoreographyVfx phrase : vfx) {
			if (phrase == null) {
				continue;
			}
			if (phrase instanceof ChoreographyVfx.ParticleBurst burst
				&& !"hero_burst".equals(burst.name())) {
				accents.add(phrase);
			} else {
				structural.add(phrase);
			}
		}
		List<ChoreographyVfx> trimmedAccents = keepTopByIndex(accents, keepRatio);
		List<ChoreographyVfx> out = new ArrayList<>(structural.size() + trimmedAccents.size());
		out.addAll(structural);
		out.addAll(trimmedAccents);
		out.sort(Comparator.comparingDouble(ChoreographyVfx::timeSeconds));
		return List.copyOf(out);
	}

	private static <T> List<T> keepTopByIndex(List<T> items, double keepRatio) {
		if (items == null || items.isEmpty() || keepRatio <= 0) {
			return List.of();
		}
		if (keepRatio >= 0.999) {
			return items;
		}
		int keep = Math.max(0, (int) Math.round(items.size() * keepRatio));
		if (keep <= 0) {
			return List.of();
		}
		return List.copyOf(items.subList(0, Math.min(keep, items.size())));
	}
}
