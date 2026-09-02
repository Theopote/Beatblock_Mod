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

	/**
	 * 仅追加动画，同时替换 smart-automap 镜头与 VFX。
	 * <p>
	 * 用于需要「保留现有动画、重编镜头/粒子」的场景；勿用 boolean 参数表达此语义。
	 */
	public static ChoreographyCompileOptions appendAnimationsReplaceCameraAndVfx() {
		ContentReplacePolicy replaceSmartAutomap = ContentReplacePolicy.replaceGenerator(TimelineGeneratorIds.SMART_AUTOMAP);
		return new ChoreographyCompileOptions(
			ContentReplacePolicy.append(),
			replaceSmartAutomap,
			replaceSmartAutomap
		);
	}

	/** 仅编译动画轨道时的便捷选项（镜头 / VFX 策略不参与 {@link ChoreographyPlanCompiler#compileAnimationEvents}）。 */
	public static ChoreographyCompileOptions animationOnly(ReplaceMode animationMode) {
		ReplaceMode mode = animationMode != null ? animationMode : ReplaceMode.APPEND;
		return new ChoreographyCompileOptions(mode, ReplaceMode.APPEND, ReplaceMode.APPEND);
	}

	/**
	 * 段落编辑 / Phrase → Section 重编：只清除并重写指定 {@code sectionIndex} 的 smart-automap 内容。
	 */
	public static ChoreographyCompileOptions forSection(int sectionIndex) {
		ContentReplacePolicy replaceSection = ContentReplacePolicy.replaceSection(sectionIndex);
		return new ChoreographyCompileOptions(
			replaceSection,
			replaceSection,
			replaceSection,
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
