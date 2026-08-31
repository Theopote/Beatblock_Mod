package com.beatblock.automap.engine;

/**
 * Smart Auto Map 运行时段落：起止时间（秒）、{@link SectionType} 与可选标签。
 * <p>
 * 只读 beatmap 分析段落见 {@link com.beatblock.audio.beatmap.MusicSection}（毫秒、{@code SectionLabel}）。
 */
public final class StructuralSection {

	private final double startSeconds;
	private final double endSeconds;
	private final SectionType type;
	private final String label;

	public StructuralSection(double startSeconds, double endSeconds, SectionType type) {
		this(startSeconds, endSeconds, type, null);
	}

	public StructuralSection(double startSeconds, double endSeconds, SectionType type, String label) {
		this.startSeconds = Math.max(0, startSeconds);
		this.endSeconds = Math.max(this.startSeconds, endSeconds);
		this.type = type != null ? type : SectionType.VERSE;
		this.label = label;
	}

	public double getStartSeconds() { return startSeconds; }
	public double getEndSeconds() { return endSeconds; }
	public SectionType getType() { return type; }
	public String getLabel() {
		return label != null && !label.isBlank() ? label : type.name().toLowerCase();
	}
	public double getDurationSeconds() { return endSeconds - startSeconds; }
}
