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
	private final double confidence;

	public StructuralSection(double startSeconds, double endSeconds, SectionType type) {
		this(startSeconds, endSeconds, type, null, 1.0);
	}

	public StructuralSection(double startSeconds, double endSeconds, SectionType type, String label) {
		this(startSeconds, endSeconds, type, label, 1.0);
	}

	public StructuralSection(
		double startSeconds,
		double endSeconds,
		SectionType type,
		String label,
		double confidence
	) {
		this.startSeconds = Math.max(0, startSeconds);
		this.endSeconds = Math.max(this.startSeconds, endSeconds);
		this.type = type != null ? type : SectionType.VERSE;
		this.label = label;
		this.confidence = Math.max(0.0, Math.min(1.0, confidence));
	}

	public double getStartSeconds() { return startSeconds; }
	public double getEndSeconds() { return endSeconds; }
	public SectionType getType() { return type; }
	public String getLabel() {
		return label != null && !label.isBlank() ? label : type.name().toLowerCase();
	}
	public double getDurationSeconds() { return endSeconds - startSeconds; }
	public double getConfidence() { return confidence; }
}
