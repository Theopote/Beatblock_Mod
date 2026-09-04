package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyLayer;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.engine.SectionType;
import org.jspecify.annotations.Nullable;

/**
 * HERO 层触发与编排规则：段落入口 / 高潮的一次性全强度跨目标动作。
 * <p>
 * 与 Phrase（段内重复）不同，Hero 每个合格段落至多触发一次。
 */
public final class ChoreographyHeroSelection {

	private ChoreographyHeroSelection() {}

	/** DROP / CHORUS / BUILD / PRE_CHORUS 生成 Hero；其余段落仅 Accent + Phrase。 */
	public static boolean isEligible(@Nullable SectionType sectionType) {
		if (sectionType == null) return false;
		return switch (sectionType) {
			case DROP, CHORUS, BUILD, PRE_CHORUS -> true;
			default -> false;
		};
	}

	/**
	 * 段内第一次达到能量门槛的 kick：对应段落入口或高潮起势。
	 */
	public static TriggerSpec trigger(@Nullable SectionType sectionType) {
		float minEnergy = switch (sectionType != null ? sectionType : SectionType.DROP) {
			case DROP -> 0.70f;
			case CHORUS -> 0.75f;
			case BUILD, PRE_CHORUS -> 0.80f;
			default -> 0.85f;
		};
		return new TriggerSpec.FirstFeature("kick", minEnergy);
	}

	public static SpatialPatternSpec spatial(@Nullable SectionType sectionType) {
		SectionType type = sectionType != null ? sectionType : SectionType.DROP;
		return switch (type) {
			case DROP -> SpatialPatternSpec.of(SpatialMotifId.EXPLODE, MotifAxis.RADIAL);
			case CHORUS -> SpatialPatternSpec.of(SpatialMotifId.WAVE, MotifAxis.Z);
			case BUILD -> SpatialPatternSpec.of(SpatialMotifId.CASCADE, MotifAxis.X);
			case PRE_CHORUS -> SpatialPatternSpec.of(SpatialMotifId.SWEEP, MotifAxis.X);
			default -> SpatialPatternSpec.of(SpatialMotifId.EXPLODE, MotifAxis.RADIAL);
		};
	}

	public static MotionPresetSpec motion(@Nullable SectionType sectionType) {
		SectionType type = sectionType != null ? sectionType : SectionType.DROP;
		return switch (type) {
			case DROP -> new MotionPresetSpec("jump", 0.6, true, 5f);
			case BUILD, PRE_CHORUS -> new MotionPresetSpec("rise", 0.55, true, 4.5f);
			case CHORUS -> new MotionPresetSpec("bounce", 0.55, true, 4.5f);
			default -> new MotionPresetSpec("jump", 0.6, true, 5f);
		};
	}

	/** Hero 同时爆发，不做 cascade stagger。 */
	public static TimingPatternSpec timing() {
		return new TimingPatternSpec.Simultaneous();
	}

	public static IntensityEnvelope intensity() {
		return IntensityEnvelope.flat(1.0f);
	}

	public static VariationSpec variation() {
		return VariationSpec.none();
	}

	public static @Nullable ChoreographyPhrase phraseForSection(
		int sectionIndex,
		@Nullable SectionType sectionType,
		TargetSet targets
	) {
		if (!isEligible(sectionType) || targets == null || targets.size() < 2) {
			return null;
		}
		return new ChoreographyPhrase(
			trigger(sectionType),
			targets,
			spatial(sectionType),
			motion(sectionType),
			timing(),
			intensity(),
			variation(),
			sectionIndex,
			ChoreographyTimingSnap.BAR,
			ChoreographyLayer.HERO
		);
	}
}
