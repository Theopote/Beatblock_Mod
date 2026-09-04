package com.beatblock.automap.choreography;

import com.beatblock.timeline.TimelineAnimationEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 空间 Phrase 展开后的批次身份：预算裁剪按整组保留或丢弃，避免拆烂放射 / 镜像 / 波浪等图形。
 */
public final class ChoreographyPhraseBatchSupport {

	public static final String PARAM_PHRASE_INSTANCE_ID = "phraseInstanceId";
	public static final String PARAM_CHOREOGRAPHY_PHRASE_ID = "choreographyPhraseId";
	public static final String PARAM_PHRASE_TARGET_COUNT = "phraseTargetCount";
	public static final String PARAM_PHRASE_ATOMIC = "phraseAtomic";

	private ChoreographyPhraseBatchSupport() {}

	public static Map<String, Object> tagAtomicBatch(
		Map<String, Object> params,
		String phraseInstanceId,
		String choreographyPhraseId,
		int phraseTargetCount
	) {
		Map<String, Object> copy = params != null ? new HashMap<>(params) : new HashMap<>();
		String instanceId = phraseInstanceId != null ? phraseInstanceId.trim() : "";
		if (instanceId.isEmpty()) return copy;
		copy.put(PARAM_PHRASE_INSTANCE_ID, instanceId);
		if (choreographyPhraseId != null && !choreographyPhraseId.isBlank()) {
			copy.put(PARAM_CHOREOGRAPHY_PHRASE_ID, choreographyPhraseId.trim());
		}
		copy.put(PARAM_PHRASE_TARGET_COUNT, Math.max(1, phraseTargetCount));
		copy.put(PARAM_PHRASE_ATOMIC, true);
		return copy;
	}

	public static String phraseInstanceIdOf(TimelineAnimationEvent event) {
		if (event == null) return "";
		Object raw = event.getParameters().get(PARAM_PHRASE_INSTANCE_ID);
		return raw != null ? String.valueOf(raw).trim() : "";
	}

	public static boolean isAtomicBatch(TimelineAnimationEvent event) {
		if (event == null) return false;
		Object raw = event.getParameters().get(PARAM_PHRASE_ATOMIC);
		if (raw instanceof Boolean bool) return bool;
		if (raw != null) return Boolean.parseBoolean(String.valueOf(raw));
		return !phraseInstanceIdOf(event).isEmpty();
	}

	public static int phraseTargetCountOf(TimelineAnimationEvent event) {
		if (event == null) return 1;
		Object raw = event.getParameters().get(PARAM_PHRASE_TARGET_COUNT);
		if (raw instanceof Number number) return Math.max(1, number.intValue());
		if (raw != null) {
			try {
				return Math.max(1, Integer.parseInt(String.valueOf(raw).trim()));
			} catch (NumberFormatException ignored) {
				return 1;
			}
		}
		return 1;
	}

	public static String grammarInstanceId(int phraseOrdinal, int triggerSequenceIndex) {
		return "grammar:" + Math.max(0, phraseOrdinal) + ":t" + Math.max(0, triggerSequenceIndex);
	}

	public static String spatialInstanceId(int phraseOrdinal) {
		return "spatial:" + Math.max(0, phraseOrdinal);
	}
}
