package com.beatblock.automap.choreography;

import com.beatblock.timeline.generation.ContentReplacePolicy;
import com.beatblock.timeline.generation.GenerationSession;
import com.beatblock.timeline.generation.TimelineGeneratorIds;
import org.jspecify.annotations.Nullable;

/**
 * 编舞计划编译选项：动画 / 镜头 / VFX 各自独立的替换策略与生成批次。
 */
public record ChoreographyCompileOptions(
	ContentReplacePolicy animationMode,
	ContentReplacePolicy cameraMode,
	ContentReplacePolicy vfxMode,
	double minGapSeconds,
	@Nullable GenerationSession generationSession
) {
	public static final double DEFAULT_MIN_GAP_SECONDS = 0.08;

	public ChoreographyCompileOptions {
		animationMode = animationMode != null ? animationMode : ContentReplacePolicy.append();
		cameraMode = cameraMode != null ? cameraMode : ContentReplacePolicy.append();
		vfxMode = vfxMode != null ? vfxMode : ContentReplacePolicy.append();
		minGapSeconds = minGapSeconds > 0 ? minGapSeconds : DEFAULT_MIN_GAP_SECONDS;
	}

	public ChoreographyCompileOptions(
		ContentReplacePolicy animationMode,
		ContentReplacePolicy cameraMode,
		ContentReplacePolicy vfxMode
	) {
		this(animationMode, cameraMode, vfxMode, DEFAULT_MIN_GAP_SECONDS, null);
	}

	public ChoreographyCompileOptions(ReplaceMode animationMode, ReplaceMode cameraMode, ReplaceMode vfxMode) {
		this(
			animationMode != null ? animationMode.toPolicy() : ContentReplacePolicy.append(),
			cameraMode != null ? cameraMode.toPolicy() : ContentReplacePolicy.append(),
			vfxMode != null ? vfxMode.toPolicy() : ContentReplacePolicy.append(),
			DEFAULT_MIN_GAP_SECONDS,
			null
		);
	}

	/** Smart Auto Map 与段落编舞重编译：只替换 {@link TimelineGeneratorIds#SMART_AUTOMAP} 归属内容。 */
	public static ChoreographyCompileOptions smartAutoMap() {
		ContentReplacePolicy replaceSmartAutomap = ContentReplacePolicy.replaceGenerator(TimelineGeneratorIds.SMART_AUTOMAP);
		return new ChoreographyCompileOptions(
			replaceSmartAutomap,
			replaceSmartAutomap,
			replaceSmartAutomap,
			DEFAULT_MIN_GAP_SECONDS,
			null
		);
	}

	public GenerationSession resolveSession(com.beatblock.timeline.Timeline timeline) {
		if (generationSession != null) {
			return generationSession;
		}
		return GenerationSession.create(TimelineGeneratorIds.SMART_AUTOMAP, timeline);
	}
}
