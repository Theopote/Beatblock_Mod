package com.beatblock.timeline.generation;

/**
 * Timeline 自动生成内容的替换策略。
 * <p>
 * 在 {@link TimelineEventOrigin#AUTO_GENERATED} 之上，可按生成器 / 批次 / 段落精确替换。
 */
public sealed interface ContentReplacePolicy {

	record Append() implements ContentReplacePolicy {}

	record ReplaceAll() implements ContentReplacePolicy {}

	/** 替换全部 {@code AUTO_GENERATED}（含未标记 generator 的 legacy 内容）。 */
	record ReplaceGenerated() implements ContentReplacePolicy {}

	/**
	 * 仅替换指定 {@code generatorId} 的内容。
	 *
	 * @param includeLegacyUntagged 为 true 时，同时清除无 {@code generatorId} 的 legacy 自动内容
	 */
	record ReplaceGenerator(String generatorId, boolean includeLegacyUntagged) implements ContentReplacePolicy {
		public ReplaceGenerator(String generatorId) {
			this(generatorId, true);
		}
	}

	record ReplaceGeneration(String generationId) implements ContentReplacePolicy {}

	record ReplaceSection(int sectionIndex) implements ContentReplacePolicy {}

	static ContentReplacePolicy append() {
		return new Append();
	}

	static ContentReplacePolicy replaceAll() {
		return new ReplaceAll();
	}

	static ContentReplacePolicy replaceGenerated() {
		return new ReplaceGenerated();
	}

	static ContentReplacePolicy replaceGenerator(String generatorId) {
		return new ReplaceGenerator(generatorId, true);
	}

	static ContentReplacePolicy replaceGenerator(String generatorId, boolean includeLegacyUntagged) {
		return new ReplaceGenerator(generatorId, includeLegacyUntagged);
	}

	static ContentReplacePolicy replaceGeneration(String generationId) {
		return new ReplaceGeneration(generationId);
	}

	static ContentReplacePolicy replaceSection(int sectionIndex) {
		return new ReplaceSection(sectionIndex);
	}
}
