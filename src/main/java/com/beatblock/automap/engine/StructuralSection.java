package com.beatblock.automap.engine;

/**
 * Smart Auto Map 运行时段落：起止时间（秒）与 {@link SectionType}，用于镜头与动画密度。
 * <p>
 * 只读 beatmap 分析段落见 {@link com.beatblock.audio.beatmap.MusicSection}（毫秒、{@code SectionLabel}）。
 */
public final class StructuralSection {

	private final double startSeconds;
	private final double endSeconds;
	private final SectionType type;

	public StructuralSection(double startSeconds, double endSeconds, SectionType type) {
		this.startSeconds = Math.max(0, startSeconds);
		this.endSeconds = Math.max(this.startSeconds, endSeconds);
		this.type = type != null ? type : SectionType.VERSE;
	}

	public double getStartSeconds() { return startSeconds; }
	public double getEndSeconds() { return endSeconds; }
	public SectionType getType() { return type; }
	public double getDurationSeconds() { return endSeconds - startSeconds; }
}
