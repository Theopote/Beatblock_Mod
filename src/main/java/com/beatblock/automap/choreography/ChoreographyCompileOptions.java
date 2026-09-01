package com.beatblock.automap.choreography;

/**
 * 编舞计划编译选项：动画 / 镜头 / VFX 各自独立的替换策略。
 */
public record ChoreographyCompileOptions(
	ReplaceMode animationMode,
	ReplaceMode cameraMode,
	ReplaceMode vfxMode
) {
	public ChoreographyCompileOptions {
		animationMode = animationMode != null ? animationMode : ReplaceMode.APPEND;
		cameraMode = cameraMode != null ? cameraMode : ReplaceMode.APPEND;
		vfxMode = vfxMode != null ? vfxMode : ReplaceMode.APPEND;
	}

	/** Smart Auto Map 与段落编舞重编译默认：只替换自动生成草稿。 */
	public static ChoreographyCompileOptions smartAutoMap() {
		return new ChoreographyCompileOptions(
			ReplaceMode.REPLACE_GENERATED,
			ReplaceMode.REPLACE_GENERATED,
			ReplaceMode.REPLACE_GENERATED
		);
	}
}
