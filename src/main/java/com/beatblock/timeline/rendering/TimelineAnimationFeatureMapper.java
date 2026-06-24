package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.FeatureEvent;
import com.beatblock.timeline.FeatureTrack;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/** 音频特征轨 → 方块/自动动画事件映射（拖放音频后填充动画轨）。 */
public final class TimelineAnimationFeatureMapper {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineAnimationFeatureMapper.class);

	private TimelineAnimationFeatureMapper() {}

	public static void populateFromAudioFeatures(Timeline timeline, int targetRowIndex, Supplier<String> targetObjectIdSupplier) {
		if (timeline == null) return;
		boolean toBlockTrack = targetRowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK;
		boolean toAutoTrack = targetRowIndex == TimelineTrackMeta.ROW_ANIM_AUTO;
		if (!toBlockTrack && !toAutoTrack) return;

		int bindingAdded = AnimationBindingEngine.applyRules(timeline, targetRowIndex, false);
		if (bindingAdded > 0) {
			LOGGER.info("BeatBlock Timeline: generated {} animation events from binding rules", bindingAdded);
			return;
		}

		String clipGenerationMode = resolveClipGenerationMode(timeline);
		boolean demucsSeparated = isDemucsSeparatedTimeline(timeline);
		String mappingPreset = resolveDemucsMappingPreset(timeline, toBlockTrack);
		double durationScale = durationScaleForPreset(mappingPreset);
		float energyThresholdScale = energyThresholdScaleForPreset(mappingPreset);
		double minGapScale = minGapScaleForPreset(mappingPreset);
		durationScale *= readScaleMetadata(timeline, "demucsMapDurationScale", 1.0, 0.5, 2.0);
		energyThresholdScale *= (float) readScaleMetadata(timeline, "demucsMapEnergyScale", 1.0, 0.6, 1.6);
		minGapScale *= readScaleMetadata(timeline, "demucsMapGapScale", 1.0, 0.5, 2.0);

		if (toBlockTrack) {
			pruneGeneratedAnimationEventsOnFeatureTracks(timeline);
			pruneGeneratedAnimationEventsOnTrack(timeline, Timeline.TRACK_ID_ANIMATION_BLOCK);
		} else {
			pruneGeneratedAnimationEventsOnTrack(timeline, Timeline.TRACK_ID_ANIMATION_AUTO);
		}

		String targetObjectId = targetObjectIdSupplier != null ? targetObjectIdSupplier.get() : "default";
		int added = 0;
		Map<String, Double> lastAcceptedTimeByFeature = new HashMap<>();

		for (Map.Entry<String, FeatureTrack> entry : timeline.getFeatureTracks().entrySet()) {
			String featureKey = entry.getKey();
			FeatureTrack track = entry.getValue();
			if (track == null || track.getEvents().isEmpty()) continue;

			AnimationMappingRule rule = selectAnimationRule(featureKey, demucsSeparated, toBlockTrack);
			if (rule == null) continue;
			added += addAnimationEventsForFeatureTrack(
				timeline,
				toBlockTrack,
				demucsSeparated,
				clipGenerationMode,
				featureKey,
				track,
				rule,
				targetObjectId,
				lastAcceptedTimeByFeature,
				durationScale,
				energyThresholdScale,
				minGapScale
			);
		}

		timeline.sortAll();
		LOGGER.info("BeatBlock Timeline: mapped dropped audio into {} animation events on {} track (preset={})",
			added, toBlockTrack ? "block-feature" : "auto", mappingPreset);
	}

	private static String resolveClipGenerationMode(Timeline timeline) {
		if (timeline == null) return "mixed";
		Object value = timeline.getMetadata("featureClipGenerationMode");
		if (value == null) return "mixed";
		String mode = value.toString().trim().toLowerCase(Locale.ROOT);
		if ("trigger".equals(mode) || "sustain".equals(mode) || "mixed".equals(mode)) {
			return mode;
		}
		return "mixed";
	}

	private static int addAnimationEventsForFeatureTrack(
		Timeline timeline,
		boolean toBlockTrack,
		boolean demucsSeparated,
		String clipGenerationMode,
		String featureKey,
		FeatureTrack track,
		AnimationMappingRule rule,
		String targetObjectId,
		Map<String, Double> lastAcceptedTimeByFeature,
		double durationScale,
		float energyThresholdScale,
		double minGapScale
	) {
		if (track == null || track.getEvents().isEmpty() || rule == null) return 0;
		if (shouldUseSustainGeneration(featureKey, demucsSeparated, toBlockTrack, clipGenerationMode)) {
			return addSustainAnimationEventsFromFeatureTrack(
				timeline,
				toBlockTrack,
				featureKey,
				track,
				rule,
				targetObjectId,
				durationScale,
				energyThresholdScale,
				minGapScale
			);
		}
		int added = 0;
		for (FeatureEvent event : track.getEvents()) {
			added += addAnimationEventFromSource(
				timeline, toBlockTrack, event.getTimeSeconds(),
				event.getEnergy(), rule,
				targetObjectId, featureKey, lastAcceptedTimeByFeature,
				durationScale, energyThresholdScale, minGapScale
			);
		}
		return added;
	}

	static boolean shouldUseSustainGeneration(String featureKey, boolean demucsSeparated, boolean toBlockTrack, String clipGenerationMode) {
		if (featureKey == null || featureKey.isBlank()) return false;
		if ("trigger".equalsIgnoreCase(clipGenerationMode)) return false;
		if ("sustain".equalsIgnoreCase(clipGenerationMode)) return true;
		if (!demucsSeparated || !toBlockTrack) return false;
		String key = featureKey.trim().toLowerCase(Locale.ROOT);
		return "bass".equals(key) || "vocals".equals(key) || "other".equals(key);
	}

	private static void pruneGeneratedAnimationEventsOnFeatureTracks(Timeline timeline) {
		if (timeline == null) return;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isBlockAnimationFeatureTrackId(track.getId())) continue;
			pruneGeneratedAnimationEventsOnTrack(timeline, track.getId());
		}
	}

	private static void pruneGeneratedAnimationEventsOnTrack(Timeline timeline, String trackId) {
		if (timeline == null || trackId == null || trackId.isBlank()) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;

		List<String> emptyClipIds = new ArrayList<>();
		boolean changed = false;
		for (Clip clip : track.getClips()) {
			if (clip == null) continue;
			List<String> removeEventIds = new ArrayList<>();
			for (TimelineEvent event : clip.getEvents()) {
				if (event == null || event.getType() != EventType.ANIMATION) continue;
				if (isGeneratedMappingAnimationEvent(event)) {
					removeEventIds.add(event.getId());
				}
			}
			for (String eventId : removeEventIds) {
				if (clip.removeEvent(eventId)) changed = true;
			}
			if (clip.getEvents().isEmpty()) {
				emptyClipIds.add(clip.getId());
			}
		}
		for (String clipId : emptyClipIds) {
			if (track.removeClip(clipId)) changed = true;
		}
		if (changed) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	public static boolean isGeneratedMappingAnimationEvent(TimelineEvent event) {
		if (event == null) return false;
		Object generatedBy = event.getParameters().get("generatedBy");
		if (generatedBy == null) return false;
		String marker = generatedBy.toString().trim().toLowerCase(Locale.ROOT);
		return marker.startsWith("audio-asset-drop");
	}

	private static int addSustainAnimationEventsFromFeatureTrack(
		Timeline timeline,
		boolean toBlockTrack,
		String sourceFeature,
		FeatureTrack track,
		AnimationMappingRule rule,
		String targetObjectId,
		double durationScale,
		float energyThresholdScale,
		double minGapScale
	) {
		if (track == null || track.getEvents().isEmpty() || rule == null) return 0;

		double featureDurationScale = readFeatureScaleMetadata(timeline, sourceFeature, "duration", 1.0, 0.5, 2.0);
		float featureEnergyScale = (float) readFeatureScaleMetadata(timeline, sourceFeature, "energy", 1.0, 0.6, 1.6);
		double featureGapScale = readFeatureScaleMetadata(timeline, sourceFeature, "gap", 1.0, 0.5, 2.0);

		double effectiveDurationScale = durationScale * featureDurationScale;
		float effectiveEnergyThresholdScale = energyThresholdScale * featureEnergyScale;
		double effectiveMinGapScale = minGapScale * featureGapScale;

		float minEnergy = Math.max(0f, Math.min(1f, rule.minEnergy() * effectiveEnergyThresholdScale));
		double minGap = Math.max(0.02, rule.minGapSeconds() * effectiveMinGapScale);
		double linkGap = Math.max(0.10, minGap * 2.0);
		double baseDuration = Math.max(0.08, rule.baseDurationSeconds() * effectiveDurationScale);

		List<FeatureEvent> sorted = new ArrayList<>(track.getEvents());
		sorted.sort(Comparator.comparingDouble(FeatureEvent::getTimeSeconds));

		double windowStart = -1;
		double windowLast = -1;
		float windowPeak = 0f;
		int added = 0;

		for (FeatureEvent e : sorted) {
			double t = e.getTimeSeconds();
			float energy = Math.max(0f, Math.min(1f, e.getEnergy()));
			if (energy < minEnergy) continue;

			if (windowStart < 0) {
				windowStart = t;
				windowLast = t;
				windowPeak = energy;
				continue;
			}

			if ((t - windowLast) <= linkGap) {
				windowLast = t;
				windowPeak = Math.max(windowPeak, energy);
				continue;
			}

			added += emitSustainWindow(
				timeline,
				toBlockTrack,
				rule,
				targetObjectId,
				sourceFeature,
				windowStart,
				windowLast,
				windowPeak,
				baseDuration,
				minGap,
				featureDurationScale,
				featureEnergyScale,
				featureGapScale,
				durationScale,
				energyThresholdScale,
				minGapScale,
				minEnergy
			);

			windowStart = t;
			windowLast = t;
			windowPeak = energy;
		}

		if (windowStart >= 0) {
			added += emitSustainWindow(
				timeline,
				toBlockTrack,
				rule,
				targetObjectId,
				sourceFeature,
				windowStart,
				windowLast,
				windowPeak,
				baseDuration,
				minGap,
				featureDurationScale,
				featureEnergyScale,
				featureGapScale,
				durationScale,
				energyThresholdScale,
				minGapScale,
				minEnergy
			);
		}

		return added;
	}

	private static int emitSustainWindow(
		Timeline timeline,
		boolean toBlockTrack,
		AnimationMappingRule rule,
		String targetObjectId,
		String sourceFeature,
		double windowStart,
		double windowLast,
		float peakEnergy,
		double baseDuration,
		double minGap,
		double featureDurationScale,
		float featureEnergyScale,
		double featureGapScale,
		double durationScale,
		float energyThresholdScale,
		double minGapScale,
		float minEnergy
	) {
		double span = Math.max(0.0, windowLast - windowStart);
		double duration = Math.max(0.10, span + baseDuration * 0.45);
		duration = Math.max(duration, minGap * 0.60);
		duration = Math.min(duration, Math.max(baseDuration * 3.2, 0.90));

		Map<String, Object> params = new HashMap<>();
		params.put("mode", TimelineAnimationActionMode.ANIMATE.name());
		params.put("energy", peakEnergy);
		params.put("energyThreshold", minEnergy);
		params.put("energyMapping", "linear");
		params.put("sourceFeature", sourceFeature);
		params.put("sourceStem", rule.sourceStem());
		params.put("mappingProfile", "demucs-aware");
		params.put("mappingPreset", resolvePresetLabel(durationScale, energyThresholdScale, minGapScale));
		params.put("featureDurationScale", featureDurationScale);
		params.put("featureEnergyScale", featureEnergyScale);
		params.put("featureGapScale", featureGapScale);
		params.put("clipGenerationMode", "sustain");
		params.put("generatedBy", "audio-asset-drop-sustain");

		TimelineAnimationEvent ev = new TimelineAnimationEvent(
			"",
			windowStart,
			duration,
			rule.animationType(),
			targetObjectId,
			peakEnergy,
			params
		);
		String trackId = toBlockTrack
			? Timeline.blockAnimationFeatureTrackId(sourceFeature)
			: Timeline.TRACK_ID_ANIMATION_AUTO;
		if (toBlockTrack) ensureBlockAnimationFeatureTrack(timeline, sourceFeature);
		TimelineDraftWriter.writeEvent(timeline, trackId, ev, TimelineEventOrigin.AUTO_GENERATED);
		return 1;
	}

	private static void resetBlockAnimationFeatureTracks(Timeline timeline) {
		List<String> ids = timeline.getTracks().stream()
			.map(Track::getId)
			.filter(Timeline::isBlockAnimationFeatureTrackId)
			.toList();
		for (String id : ids) {
			timeline.removeTrack(id);
		}
	}

	private static Track ensureBlockAnimationFeatureTrack(Timeline timeline, String featureKey) {
		String trackId = Timeline.blockAnimationFeatureTrackId(featureKey);
		Track track = timeline.getTrack(trackId);
		if (track != null) return track;
		track = new Track(trackId, TrackRegistry.localizedName(featureKey), TrackType.ANIMATION);
		timeline.addTrack(track);
		return track;
	}

	private static int addAnimationEventFromSource(
		Timeline timeline,
		boolean toBlockTrack,
		double timeSeconds,
		float rawEnergy,
		AnimationMappingRule rule,
		String targetObjectId,
		String sourceFeature,
		Map<String, Double> lastAcceptedTimeByFeature,
		double durationScale,
		float energyThresholdScale,
		double minGapScale
	) {
		if (rule == null) return 0;
		double featureDurationScale = readFeatureScaleMetadata(timeline, sourceFeature, "duration", 1.0, 0.5, 2.0);
		float featureEnergyScale = (float) readFeatureScaleMetadata(timeline, sourceFeature, "energy", 1.0, 0.6, 1.6);
		double featureGapScale = readFeatureScaleMetadata(timeline, sourceFeature, "gap", 1.0, 0.5, 2.0);

		double effectiveDurationScale = durationScale * featureDurationScale;
		float effectiveEnergyThresholdScale = energyThresholdScale * featureEnergyScale;
		double effectiveMinGapScale = minGapScale * featureGapScale;

		float energy = Math.max(0f, Math.min(1f, rawEnergy));
		float minEnergy = Math.max(0f, Math.min(1f, rule.minEnergy() * effectiveEnergyThresholdScale));
		if (energy < minEnergy) return 0;
		double minGap = Math.max(0.02, rule.minGapSeconds() * effectiveMinGapScale);

		Double lastAccepted = lastAcceptedTimeByFeature.get(sourceFeature);
		if (lastAccepted != null && timeSeconds < lastAccepted + minGap) {
			return 0;
		}

		double duration = Math.max(0.05, rule.baseDurationSeconds() * effectiveDurationScale * (0.70 + energy * 0.75));
		Map<String, Object> params = new HashMap<>();
		params.put("mode", TimelineAnimationActionMode.ANIMATE.name());
		params.put("energy", energy);
		params.put("energyThreshold", minEnergy);
		params.put("energyMapping", "linear");
		params.put("sourceFeature", sourceFeature);
		params.put("sourceStem", rule.sourceStem());
		params.put("mappingProfile", "demucs-aware");
		params.put("mappingPreset", resolvePresetLabel(durationScale, energyThresholdScale, minGapScale));
		params.put("featureDurationScale", featureDurationScale);
		params.put("featureEnergyScale", featureEnergyScale);
		params.put("featureGapScale", featureGapScale);
		params.put("clipGenerationMode", "trigger");
		params.put("generatedBy", "audio-asset-drop-trigger");

		TimelineAnimationEvent ev = new TimelineAnimationEvent(
			"",
			timeSeconds,
			duration,
			rule.animationType(),
			targetObjectId,
			energy,
			params
		);
		String trackId = toBlockTrack
			? Timeline.blockAnimationFeatureTrackId(sourceFeature)
			: Timeline.TRACK_ID_ANIMATION_AUTO;
		if (toBlockTrack) ensureBlockAnimationFeatureTrack(timeline, sourceFeature);
		TimelineDraftWriter.writeEvent(timeline, trackId, ev, TimelineEventOrigin.AUTO_GENERATED);
		lastAcceptedTimeByFeature.put(sourceFeature, timeSeconds);
		return 1;
	}

	public static AnimationMappingRule selectAnimationRule(String featureKey, boolean demucsSeparated, boolean toBlockTrack) {
		if (featureKey == null || featureKey.isBlank()) return null;
		String key = featureKey.toLowerCase();

		if (demucsSeparated) {
			if (toBlockTrack) {
				return switch (key) {
					case "kick" -> new AnimationMappingRule("bounce", 0.46, 0.18f, 0.18, "drums");
					case "snare" -> new AnimationMappingRule("slide", 0.34, 0.16f, 0.24, "drums");
					case "bass" -> new AnimationMappingRule("bounce", 0.58, 0.20f, 0.32, "bass");
					default -> null;
				};
			}
			return switch (key) {
				case "hihat" -> new AnimationMappingRule("pulse", 0.20, 0.12f, 0.10, "drums");
				case "hihat_open" -> new AnimationMappingRule("pulse", 0.28, 0.16f, 0.16, "drums");
				case "snare_hi" -> new AnimationMappingRule("slide", 0.26, 0.16f, 0.20, "drums");
				case "vocals" -> new AnimationMappingRule("slide", 0.52, 0.20f, 0.48, "vocals");
				case "other" -> new AnimationMappingRule("pulse", 0.34, 0.18f, 0.30, "other");
				case "bass" -> new AnimationMappingRule("pulse", 0.40, 0.24f, 0.42, "bass");
				default -> null;
			};
		}

		if (toBlockTrack) {
			return switch (key) {
				case "kick", "low" -> new AnimationMappingRule("bounce", 0.48, 0.18f, 0.20, "mix");
				case "snare", "mid" -> new AnimationMappingRule("slide", 0.36, 0.16f, 0.24, "mix");
				default -> null;
			};
		}
		return switch (key) {
			case "hihat", "high" -> new AnimationMappingRule("pulse", 0.24, 0.12f, 0.12, "mix");
			case "snare_hi", "mid" -> new AnimationMappingRule("slide", 0.30, 0.16f, 0.22, "mix");
			default -> null;
		};
	}

	private static boolean isDemucsSeparatedTimeline(Timeline timeline) {
		if (timeline == null) return false;
		Object value = timeline.getMetadata("separationMode");
		return value != null && "demucs".equalsIgnoreCase(value.toString().trim());
	}

	private static String resolveDemucsMappingPreset(Timeline timeline, boolean toBlockTrack) {
		if (timeline != null) {
			Object configured = timeline.getMetadata("demucsMappingPreset");
			if (configured != null) {
				String v = configured.toString().trim().toLowerCase();
				if ("drive".equals(v) || "detail".equals(v) || "balanced".equals(v)) {
					return v;
				}
			}
		}
		return toBlockTrack ? "drive" : "detail";
	}

	private static double readScaleMetadata(Timeline timeline, String key, double defaultValue, double min, double max) {
		if (timeline == null || key == null || key.isBlank()) return defaultValue;
		Object raw = timeline.getMetadata(key);
		if (raw == null) return defaultValue;
		double v;
		if (raw instanceof Number n) {
			v = n.doubleValue();
		} else {
			try {
				v = Double.parseDouble(raw.toString().trim());
			} catch (Exception e) {
				return defaultValue;
			}
		}
		if (Double.isNaN(v) || Double.isInfinite(v)) return defaultValue;
		return Math.max(min, Math.min(max, v));
	}

	private static double durationScaleForPreset(String preset) {
		return switch (preset) {
			case "drive" -> 1.18;
			case "detail" -> 0.92;
			default -> 1.0;
		};
	}

	private static float energyThresholdScaleForPreset(String preset) {
		return switch (preset) {
			case "drive" -> 0.88f;
			case "detail" -> 1.10f;
			default -> 1.0f;
		};
	}

	private static double minGapScaleForPreset(String preset) {
		return switch (preset) {
			case "drive" -> 0.85;
			case "detail" -> 1.18;
			default -> 1.0;
		};
	}

	private static String resolvePresetLabel(double durationScale, float energyThresholdScale, double minGapScale) {
		if (durationScale > 1.05 && energyThresholdScale < 0.95f && minGapScale < 0.95) return "drive";
		if (durationScale < 0.97 && energyThresholdScale > 1.05f && minGapScale > 1.05) return "detail";
		return "balanced";
	}

	private static double readFeatureScaleMetadata(
		Timeline timeline,
		String featureKey,
		String scaleType,
		double defaultValue,
		double min,
		double max
	) {
		if (timeline == null || featureKey == null || featureKey.isBlank() || scaleType == null || scaleType.isBlank()) {
			return defaultValue;
		}
		String normalizedFeature = featureKey.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
		String normalizedScale = scaleType.trim().toLowerCase();
		String metadataKey = "demucsFeat" + capitalize(normalizedScale) + "_" + normalizedFeature;
		return readScaleMetadata(timeline, metadataKey, defaultValue, min, max);
	}

	private static String capitalize(String s) {
		if (s == null || s.isBlank()) return "";
		if (s.length() == 1) return s.toUpperCase();
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	public record AnimationMappingRule(
		String animationType,
		double baseDurationSeconds,
		float minEnergy,
		double minGapSeconds,
		String sourceStem
	) {}

}
