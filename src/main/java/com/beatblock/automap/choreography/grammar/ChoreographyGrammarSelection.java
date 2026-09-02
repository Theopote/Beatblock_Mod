package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.SpatialMotifSelection;
import com.beatblock.automap.engine.SectionType;

/**
 * 按音乐段落类型选择默认编舞语法短语（smart-automap 生成路径）。
 */
public final class ChoreographyGrammarSelection {

	private ChoreographyGrammarSelection() {}

	public static TriggerSpec defaultTrigger(SectionType sectionType) {
		int interval = switch (sectionType != null ? sectionType : SectionType.BUILD) {
			case DROP -> 2;
			case INTRO, OUTRO, BREAK -> 8;
			default -> 4;
		};
		return new TriggerSpec.EveryNBeats(interval, "kick");
	}

	public static SpatialPatternSpec spatialPattern(SectionType sectionType) {
		SpatialMotifId motif = SpatialMotifSelection.forSection(sectionType);
		MotifAxis axis = SpatialMotifSelection.defaultAxis(sectionType);
		if (motif == SpatialMotifId.CASCADE && axis == MotifAxis.X) {
			return SpatialPatternSpec.leftToRight();
		}
		return SpatialPatternSpec.of(motif, axis);
	}

	public static MotionPresetSpec motion(SectionType sectionType) {
		return MotionPresetSpec.of(SpatialMotifSelection.defaultPrimitive(sectionType));
	}

	public static TimingPatternSpec timing(SectionType sectionType) {
		return TimingPatternSpec.stagger(SpatialMotifSelection.defaultPropagationDelay(sectionType));
	}

	public static IntensityEnvelope intensity(SectionType sectionType) {
		if (sectionType == null) return IntensityEnvelope.flat(0.75f);
		return switch (sectionType) {
			case BUILD, PRE_CHORUS, DROP -> IntensityEnvelope.crescendo(0.6f, 1.0f);
			case INTRO, OUTRO, BREAK -> IntensityEnvelope.flat(0.5f);
			default -> IntensityEnvelope.flat(0.75f);
		};
	}

	public static VariationSpec variation(SectionType sectionType) {
		if (sectionType == SectionType.VERSE || sectionType == SectionType.BRIDGE) {
			return VariationSpec.alternateHeight(0.3f);
		}
		return VariationSpec.none();
	}
}
