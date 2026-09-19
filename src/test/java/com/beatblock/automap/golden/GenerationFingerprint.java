package com.beatblock.automap.golden;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyVfx;
import com.beatblock.automap.choreography.grammar.ChoreographyPhrase;
import com.beatblock.automap.engine.SectionType;

/**
 * 生成结果指纹：用于跨曲风 / 预设对比，而非仅 event count &gt; 0。
 */
public record GenerationFingerprint(
	SongStyleFixture style,
	String presetName,
	int motionCount,
	int heroCount,
	int cameraCount,
	int vfxCount,
	int accentVfxCount,
	int structuralVfxCount,
	double introMotionDensity,
	double dropOrClimaxMotionDensity,
	double verseMotionDensity,
	int shakeCameraCount,
	int pushInCameraCount,
	int orbitCameraCount,
	int holdCameraCount,
	boolean hasDropEntranceFlash,
	boolean hasHeroBurst,
	boolean hasBuildSequence
) {

	public static GenerationFingerprint from(
		SongStyleFixture style,
		String presetName,
		ChoreographyPlan plan
	) {
		int heroes = 0;
		for (ChoreographyPhrase phrase : plan.choreographyPhrases()) {
			if (phrase != null && phrase.isHero()) {
				heroes++;
			}
		}
		int accentVfx = 0;
		int structuralVfx = 0;
		boolean dropFlash = false;
		boolean heroBurst = false;
		for (ChoreographyVfx vfx : plan.vfxPhrases()) {
			if (vfx instanceof ChoreographyVfx.ParticleBurst burst) {
				if ("hero_burst".equals(burst.name())) {
					structuralVfx++;
					heroBurst = true;
				} else {
					accentVfx++;
				}
			} else {
				structuralVfx++;
				if (vfx instanceof ChoreographyVfx.ScreenFlash flash
					&& flash.name() != null
					&& flash.name().contains("entrance")) {
					dropFlash = true;
				}
			}
		}
		int shake = 0;
		int push = 0;
		int orbit = 0;
		int hold = 0;
		for (ChoreographyPlan.CameraPhrase camera : plan.cameraPhrases()) {
			String movement = camera.movement() != null ? camera.movement().toUpperCase() : "";
			String action = camera.action() != null ? camera.action().toUpperCase() : "";
			String token = movement.isBlank() ? action : movement;
			if (token.contains("SHAKE")) shake++;
			if (token.contains("PUSH") || token.contains("ZOOM_IN")) push++;
			if (token.contains("ORBIT")) orbit++;
			if (token.contains("HOLD") || token.isBlank()) hold++;
		}
		return new GenerationFingerprint(
			style,
			presetName,
			plan.motionPhrases().size(),
			heroes,
			plan.cameraPhrases().size(),
			plan.vfxPhrases().size(),
			accentVfx,
			structuralVfx,
			density(plan, SectionType.INTRO),
			Math.max(density(plan, SectionType.DROP), density(plan, SectionType.CHORUS)),
			density(plan, SectionType.VERSE),
			shake,
			push,
			orbit,
			hold,
			dropFlash,
			heroBurst,
			!plan.buildSequences().isEmpty()
		);
	}

	private static double density(ChoreographyPlan plan, SectionType type) {
		double duration = 0;
		int count = 0;
		for (int i = 0; i < plan.sections().size(); i++) {
			ChoreographyPlan.SectionPlan section = plan.sections().get(i);
			if (section.sectionType() != type) {
				continue;
			}
			duration += section.durationSeconds();
			final int sectionIndex = i;
			count += (int) plan.motionPhrases().stream()
				.filter(m -> m.sectionIndex() == sectionIndex)
				.count();
		}
		if (duration <= 1e-6) {
			return 0;
		}
		return count / duration;
	}
}
