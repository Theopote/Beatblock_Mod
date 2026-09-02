package com.beatblock.timeline.project;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.AutoMapRule;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.ChoreographyVfxPersistence;
import com.beatblock.automap.choreography.DensityCurve;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.beatblock.automap.choreography.SpatialMotifPhrase;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.MotifPhaseMode;
import com.beatblock.automap.choreography.SectionEditProfile;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.choreography.TimingSnapDefaults;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Timeline;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 编舞计划与 AutoMap 配置 ↔ .osc JSON 持久化。
 */
public final class ChoreographyPlanPersistence {

	private ChoreographyPlanPersistence() {}

	public static @Nullable JsonObject toJson(Timeline timeline) {
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		AutoMapConfig config = ChoreographyPlanStore.loadConfig(timeline);
		if (plan == null && config == null) return null;
		JsonObject root = new JsonObject();
		if (plan != null) root.add("plan", planToJson(plan));
		if (config != null) root.add("config", configToJson(config));
		return root;
	}

	public static void loadInto(Timeline timeline, @Nullable JsonElement element) {
		if (timeline == null || element == null || !element.isJsonObject()) return;
		JsonObject root = element.getAsJsonObject();
		ChoreographyPlan plan = root.has("plan") ? planFromJson(root.get("plan")) : null;
		AutoMapConfig config = root.has("config") ? configFromJson(root.get("config")) : null;
		if (plan != null || config != null) {
			ChoreographyPlanStore.save(timeline, plan, config);
		}
	}

	private static JsonObject planToJson(ChoreographyPlan plan) {
		JsonObject root = new JsonObject();
		root.addProperty("structureVersion", 2);
		root.add("sections", sectionsToJson(plan.sections()));
		root.add("stageRoles", stageRolesToJson(plan.stageRoles()));
		root.add("motionPhrases", motionPhrasesToJson(plan.motionPhrases()));
		root.add("spatialMotifPhrases", spatialMotifPhrasesToJson(plan.spatialMotifPhrases()));
		if (!plan.choreographyPhrases().isEmpty()) {
			root.add("choreographyPhrases", com.beatblock.automap.choreography.grammar.ChoreographyPhrasePersistence.toJson(plan.choreographyPhrases()));
		}
		root.add("cameraPhrases", cameraPhrasesToJson(plan.cameraPhrases()));
		root.add("vfxPhrases", ChoreographyVfxPersistence.toJson(plan.vfxPhrases()));
		root.add("densityCurve", densityCurveToJson(plan.densityCurve()));
		root.add("sectionEdits", sectionEditsToJson(plan.sectionEdits()));
		if (!plan.musicalStructure().isEmpty()) {
			root.add("musicalStructure", musicalStructureToJson(plan.musicalStructure()));
		}
		return root;
	}

	private static ChoreographyPlan planFromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) return ChoreographyPlan.empty();
		JsonObject root = element.getAsJsonObject();
		return new ChoreographyPlan(
			sectionsFromJson(root.get("sections")),
			stageRolesFromJson(root.get("stageRoles")),
			motionPhrasesFromJson(root.get("motionPhrases")),
			cameraPhrasesFromJson(root.get("cameraPhrases")),
			ChoreographyVfxPersistence.fromJson(root.get("vfxPhrases")),
			densityCurveFromJson(root.get("densityCurve")),
			sectionEditsFromJson(root.get("sectionEdits")),
			musicalStructureFromJson(root.get("musicalStructure")),
			spatialMotifPhrasesFromJson(root.get("spatialMotifPhrases")),
			com.beatblock.automap.choreography.grammar.ChoreographyPhrasePersistence.fromJson(root.get("choreographyPhrases"))
		);
	}

	private static JsonObject musicalStructureToJson(ChoreographyPlan.MusicalStructure musical) {
		JsonObject root = new JsonObject();
		root.add("bars", barsToJson(musical.bars()));
		root.add("phrases", musicalPhrasesToJson(musical.phrases()));
		root.add("repeats", repeatsToJson(musical.repeats()));
		if (!musical.beatTimes().isEmpty()) {
			JsonArray beatTimes = new JsonArray();
			for (double beatTime : musical.beatTimes()) {
				beatTimes.add(beatTime);
			}
			root.add("beatTimes", beatTimes);
		}
		return root;
	}

	private static ChoreographyPlan.MusicalStructure musicalStructureFromJson(@Nullable JsonElement element) {
		if (element == null || !element.isJsonObject()) {
			return ChoreographyPlan.MusicalStructure.empty();
		}
		JsonObject root = element.getAsJsonObject();
		return new ChoreographyPlan.MusicalStructure(
			barsFromJson(root.get("bars")),
			musicalPhrasesFromJson(root.get("phrases")),
			repeatsFromJson(root.get("repeats")),
			beatTimesFromJson(root.get("beatTimes"))
		);
	}

	private static List<Double> beatTimesFromJson(@Nullable JsonElement element) {
		if (element == null || !element.isJsonArray()) return List.of();
		JsonArray arr = element.getAsJsonArray();
		List<Double> out = new ArrayList<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonPrimitive()) continue;
			out.add(arr.get(i).getAsDouble());
		}
		return out;
	}

	private static JsonArray barsToJson(List<ChoreographyPlan.BarPlan> bars) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.BarPlan bar : bars) {
			JsonObject obj = new JsonObject();
			obj.addProperty("startSeconds", bar.startSeconds());
			obj.addProperty("endSeconds", bar.endSeconds());
			obj.addProperty("barIndex", bar.barIndex());
			obj.addProperty("sectionIndex", bar.sectionIndex());
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.BarPlan> barsFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.BarPlan> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			out.add(new ChoreographyPlan.BarPlan(
				getDouble(obj, "startSeconds", 0.0),
				getDouble(obj, "endSeconds", 0.0),
				getInt(obj, "barIndex", 0),
				getInt(obj, "sectionIndex", -1)
			));
		}
		return out;
	}

	private static JsonArray musicalPhrasesToJson(List<ChoreographyPlan.MusicalPhrasePlan> phrases) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.MusicalPhrasePlan phrase : phrases) {
			JsonObject obj = new JsonObject();
			obj.addProperty("startSeconds", phrase.startSeconds());
			obj.addProperty("endSeconds", phrase.endSeconds());
			obj.addProperty("phraseIndex", phrase.phraseIndex());
			obj.addProperty("sectionIndex", phrase.sectionIndex());
			obj.addProperty("repetitionScore", phrase.repetitionScore());
			if (phrase.repeatAnchorPhraseIndex() >= 0) {
				obj.addProperty("repeatAnchorPhraseIndex", phrase.repeatAnchorPhraseIndex());
			}
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.MusicalPhrasePlan> musicalPhrasesFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.MusicalPhrasePlan> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			out.add(new ChoreographyPlan.MusicalPhrasePlan(
				getDouble(obj, "startSeconds", 0.0),
				getDouble(obj, "endSeconds", 0.0),
				getInt(obj, "phraseIndex", 0),
				getInt(obj, "sectionIndex", -1),
				getDouble(obj, "repetitionScore", 0.0),
				getInt(obj, "repeatAnchorPhraseIndex", -1)
			));
		}
		return out;
	}

	private static JsonArray repeatsToJson(List<ChoreographyPlan.RepeatGroup> repeats) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.RepeatGroup repeat : repeats) {
			JsonObject obj = new JsonObject();
			obj.addProperty("repeatGroupId", repeat.repeatGroupId());
			obj.addProperty("anchorPhraseIndex", repeat.anchorPhraseIndex());
			obj.addProperty("similarityScore", repeat.similarityScore());
			JsonArray indices = new JsonArray();
			for (int index : repeat.phraseIndices()) {
				indices.add(index);
			}
			obj.add("phraseIndices", indices);
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.RepeatGroup> repeatsFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.RepeatGroup> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			List<Integer> indices = new ArrayList<>();
			if (obj.has("phraseIndices") && obj.get("phraseIndices").isJsonArray()) {
				JsonArray phraseIndices = obj.getAsJsonArray("phraseIndices");
				for (int j = 0; j < phraseIndices.size(); j++) {
					indices.add(phraseIndices.get(j).getAsInt());
				}
			}
			out.add(new ChoreographyPlan.RepeatGroup(
				getInt(obj, "repeatGroupId", 0),
				getInt(obj, "anchorPhraseIndex", 0),
				indices,
				getDouble(obj, "similarityScore", 0.0)
			));
		}
		return out;
	}

	private static JsonObject configToJson(AutoMapConfig config) {
		JsonObject root = new JsonObject();
		root.addProperty("defaultHeightMultiplier", config.getDefaultHeightMultiplier());
		root.addProperty("minGapSeconds", config.getMinGapSeconds());
		JsonArray rules = new JsonArray();
		for (AutoMapRule rule : config.getRules()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("featureKey", rule.getFeatureKey());
			obj.addProperty("minEnergy", rule.getMinEnergy());
			obj.addProperty("animationTypeId", rule.getAnimationTypeId());
			obj.addProperty("durationSeconds", rule.getDurationSeconds());
			obj.addProperty("useEnergyForHeight", rule.isUseEnergyForHeight());
			obj.addProperty("heightMultiplier", rule.getHeightMultiplier());
			obj.addProperty("minGapSeconds", rule.getMinGapSeconds());
			if (rule.getTargetObjectId() != null) {
				obj.addProperty("targetObjectId", rule.getTargetObjectId());
			}
			rules.add(obj);
		}
		root.add("rules", rules);
		JsonObject targets = new JsonObject();
		for (Map.Entry<String, String> entry : config.getTargetByNormalizedFeature().entrySet()) {
			targets.addProperty(entry.getKey(), entry.getValue());
		}
		root.add("targetByNormalizedFeature", targets);
		return root;
	}

	private static AutoMapConfig configFromJson(JsonElement element) {
		if (element == null || !element.isJsonObject()) return AutoMapConfig.createDefault();
		JsonObject root = element.getAsJsonObject();
		float defaultHeight = root.has("defaultHeightMultiplier")
			? root.get("defaultHeightMultiplier").getAsFloat() : 3f;
		double minGap = root.has("minGapSeconds") ? root.get("minGapSeconds").getAsDouble() : 0.08;
		AutoMapConfig.Builder builder = AutoMapConfig.builder()
			.defaultHeightMultiplier(defaultHeight)
			.minGapSeconds(minGap);
		if (root.has("rules") && root.get("rules").isJsonArray()) {
			JsonArray rules = root.getAsJsonArray("rules");
			for (int i = 0; i < rules.size(); i++) {
				if (!rules.get(i).isJsonObject()) continue;
				JsonObject obj = rules.get(i).getAsJsonObject();
				builder.rule(new AutoMapRule(
					getString(obj, "featureKey", "low"),
					getFloat(obj, "minEnergy", 0f),
					getString(obj, "animationTypeId", "bounce"),
					getDouble(obj, "durationSeconds", 0.5),
					getBool(obj, "useEnergyForHeight", true),
					getFloat(obj, "heightMultiplier", 3f),
					getDouble(obj, "minGapSeconds", 0.0),
					obj.has("targetObjectId") && !obj.get("targetObjectId").isJsonNull()
						? obj.get("targetObjectId").getAsString() : null
				));
			}
		}
		if (root.has("targetByNormalizedFeature") && root.get("targetByNormalizedFeature").isJsonObject()) {
			JsonObject targets = root.getAsJsonObject("targetByNormalizedFeature");
			for (String key : targets.keySet()) {
				builder.targetForFeature(key, targets.get(key).getAsString());
			}
		}
		return builder.build();
	}

	private static JsonArray sectionsToJson(List<ChoreographyPlan.SectionPlan> sections) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.SectionPlan section : sections) {
			JsonObject obj = new JsonObject();
			obj.addProperty("startSeconds", section.startSeconds());
			obj.addProperty("endSeconds", section.endSeconds());
			obj.addProperty("sectionType", section.sectionType().name());
			obj.addProperty("label", section.label());
			obj.addProperty("confidence", section.confidence());
			obj.addProperty("source", section.source().name());
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.SectionPlan> sectionsFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.SectionPlan> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			SectionType type;
			try {
				type = SectionType.valueOf(getString(obj, "sectionType", "VERSE"));
			} catch (IllegalArgumentException e) {
				type = SectionType.VERSE;
			}
			out.add(new ChoreographyPlan.SectionPlan(
				getDouble(obj, "startSeconds", 0.0),
				getDouble(obj, "endSeconds", 0.0),
				type,
				getString(obj, "label", ""),
				getDouble(obj, "confidence", 1.0),
				parseSectionSource(getString(obj, "source", "ANALYZED"))
			));
		}
		return out;
	}

	private static JsonArray stageRolesToJson(List<ChoreographyPlan.StageRoleAssignment> roles) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.StageRoleAssignment role : roles) {
			JsonObject obj = new JsonObject();
			obj.addProperty("normalizedFeatureKey", role.normalizedFeatureKey());
			obj.addProperty("targetObjectId", role.targetObjectId());
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.StageRoleAssignment> stageRolesFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.StageRoleAssignment> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			out.add(new ChoreographyPlan.StageRoleAssignment(
				getString(obj, "normalizedFeatureKey", ""),
				getString(obj, "targetObjectId", "")
			));
		}
		return out;
	}

	private static JsonArray motionPhrasesToJson(List<ChoreographyPlan.MotionPhrase> phrases) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.MotionPhrase phrase : phrases) {
			JsonObject obj = new JsonObject();
			obj.addProperty("timeSeconds", phrase.timeSeconds());
			obj.addProperty("trackKey", phrase.trackKey());
			obj.addProperty("normalizedFeatureKey", phrase.normalizedFeatureKey());
			obj.addProperty("energy", phrase.energy());
			obj.addProperty("animationTypeId", phrase.animationTypeId());
			obj.addProperty("durationSeconds", phrase.durationSeconds());
			obj.addProperty("useEnergyForHeight", phrase.useEnergyForHeight());
			obj.addProperty("heightMultiplier", phrase.heightMultiplier());
			if (phrase.minGapSeconds() > 0) {
				obj.addProperty("minGapSeconds", phrase.minGapSeconds());
			}
			obj.addProperty("sectionIndex", phrase.sectionIndex());
			if (phrase.timingSnap() != TimingSnapDefaults.forFeatureKey(phrase.normalizedFeatureKey())) {
				obj.addProperty("timingSnap", phrase.timingSnap().name());
			}
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.MotionPhrase> motionPhrasesFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			out.add(new ChoreographyPlan.MotionPhrase(
				getDouble(obj, "timeSeconds", 0.0),
				getString(obj, "trackKey", ""),
				getString(obj, "normalizedFeatureKey", "low"),
				getFloat(obj, "energy", 0f),
				getString(obj, "animationTypeId", "bounce"),
				getDouble(obj, "durationSeconds", 0.5),
				getBool(obj, "useEnergyForHeight", true),
				getFloat(obj, "heightMultiplier", 3f),
				getDouble(obj, "minGapSeconds", 0.0),
				getInt(obj, "sectionIndex", -1),
				parseTimingSnap(obj, getString(obj, "normalizedFeatureKey", "low"))
			));
		}
		return out;
	}

	private static JsonArray spatialMotifPhrasesToJson(List<SpatialMotifPhrase> phrases) {
		JsonArray arr = new JsonArray();
		for (SpatialMotifPhrase phrase : phrases) {
			JsonObject obj = new JsonObject();
			obj.addProperty("timeSeconds", phrase.timeSeconds());
			obj.addProperty("motifId", phrase.motifId().name());
			JsonArray participants = new JsonArray();
			for (String participantId : phrase.participantIds()) {
				participants.add(participantId);
			}
			obj.add("participantIds", participants);
			obj.addProperty("axis", phrase.axis().name());
			obj.addProperty("propagationDelaySeconds", phrase.propagationDelaySeconds());
			obj.addProperty("primitiveId", phrase.primitiveId());
			obj.addProperty("phaseMode", phrase.phaseMode().name());
			obj.addProperty("energy", phrase.energy());
			obj.addProperty("durationSeconds", phrase.durationSeconds());
			obj.addProperty("useEnergyForHeight", phrase.useEnergyForHeight());
			obj.addProperty("heightMultiplier", phrase.heightMultiplier());
			obj.addProperty("sectionIndex", phrase.sectionIndex());
			if (phrase.timingSnap() != ChoreographyTimingSnap.BAR) {
				obj.addProperty("timingSnap", phrase.timingSnap().name());
			}
			arr.add(obj);
		}
		return arr;
	}

	private static List<SpatialMotifPhrase> spatialMotifPhrasesFromJson(@Nullable JsonElement element) {
		List<SpatialMotifPhrase> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			List<String> participants = new ArrayList<>();
			if (obj.has("participantIds") && obj.get("participantIds").isJsonArray()) {
				JsonArray ids = obj.getAsJsonArray("participantIds");
				for (int j = 0; j < ids.size(); j++) {
					participants.add(ids.get(j).getAsString());
				}
			}
			out.add(new SpatialMotifPhrase(
				getDouble(obj, "timeSeconds", 0.0),
				SpatialMotifId.fromValue(getString(obj, "motifId", "CASCADE")),
				participants,
				MotifAxis.fromValue(getString(obj, "axis", "X")),
				getDouble(obj, "propagationDelaySeconds", 0.06),
				getString(obj, "primitiveId", "pulse"),
				MotifPhaseMode.fromValue(getString(obj, "phaseMode", "IN_PHASE")),
				getFloat(obj, "energy", 0.8f),
				getDouble(obj, "durationSeconds", 0.5),
				getBool(obj, "useEnergyForHeight", true),
				getFloat(obj, "heightMultiplier", 4f),
				getInt(obj, "sectionIndex", -1),
				parseTimingSnap(obj, ChoreographyTimingSnap.BAR)
			));
		}
		return out;
	}

	private static JsonArray cameraPhrasesToJson(List<ChoreographyPlan.CameraPhrase> phrases) {
		JsonArray arr = new JsonArray();
		for (ChoreographyPlan.CameraPhrase phrase : phrases) {
			JsonObject obj = new JsonObject();
			obj.addProperty("timeSeconds", phrase.timeSeconds());
			obj.addProperty("action", phrase.action());
			obj.addProperty("sectionIndex", phrase.sectionIndex());
			if (!phrase.subjectKind().isBlank()) obj.addProperty("subjectKind", phrase.subjectKind());
			if (!phrase.subjectRef().isBlank()) obj.addProperty("subjectRef", phrase.subjectRef());
			if (phrase.durationSeconds() != 3.0) obj.addProperty("durationSeconds", phrase.durationSeconds());
			if (!phrase.framing().isBlank()) obj.addProperty("framing", phrase.framing());
			if (!phrase.movement().isBlank()) obj.addProperty("movement", phrase.movement());
			if (!phrase.easing().isBlank()) obj.addProperty("easing", phrase.easing());
			if (phrase.beatAligned()) obj.addProperty("beatAligned", true);
			if (phrase.timingSnap() != ChoreographyTimingSnap.BAR) {
				obj.addProperty("timingSnap", phrase.timingSnap().name());
			}
			arr.add(obj);
		}
		return arr;
	}

	private static List<ChoreographyPlan.CameraPhrase> cameraPhrasesFromJson(@Nullable JsonElement element) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			out.add(new ChoreographyPlan.CameraPhrase(
				getDouble(obj, "timeSeconds", 0.0),
				getString(obj, "action", ""),
				getInt(obj, "sectionIndex", -1),
				getString(obj, "subjectKind", ""),
				getString(obj, "subjectRef", ""),
				getDouble(obj, "durationSeconds", 3.0),
				getString(obj, "framing", ""),
				getString(obj, "movement", ""),
				getString(obj, "easing", ""),
				getBool(obj, "beatAligned", false),
				parseTimingSnap(obj, ChoreographyTimingSnap.BAR)
			));
		}
		return out;
	}

	private static JsonArray densityCurveToJson(DensityCurve curve) {
		JsonArray arr = new JsonArray();
		for (DensityCurve.Point point : curve.points()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("timeSeconds", point.timeSeconds());
			obj.addProperty("density", point.density());
			arr.add(obj);
		}
		return arr;
	}

	private static DensityCurve densityCurveFromJson(@Nullable JsonElement element) {
		if (element == null || !element.isJsonArray()) return DensityCurve.uniform(1.0);
		JsonArray arr = element.getAsJsonArray();
		List<DensityCurve.Point> points = new ArrayList<>();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			points.add(new DensityCurve.Point(
				getDouble(obj, "timeSeconds", 0.0),
				getDouble(obj, "density", 1.0)
			));
		}
		return DensityCurve.ofPoints(points);
	}

	private static JsonArray sectionEditsToJson(List<SectionEditProfile> edits) {
		JsonArray arr = new JsonArray();
		for (SectionEditProfile edit : edits) {
			JsonObject obj = new JsonObject();
			obj.addProperty("sectionIndex", edit.sectionIndex());
			obj.addProperty("motionEnabled", edit.motionEnabled());
			obj.addProperty("cameraEnabled", edit.cameraEnabled());
			obj.addProperty("vfxEnabled", edit.vfxEnabled());
			if (edit.motionAnimationTypeOverride() != null) {
				obj.addProperty("motionAnimationTypeOverride", edit.motionAnimationTypeOverride());
			}
			if (edit.densityThresholdOverride() != null) {
				obj.addProperty("densityThresholdOverride", edit.densityThresholdOverride());
			}
			obj.addProperty("timeOffsetSeconds", edit.timeOffsetSeconds());
			obj.addProperty("energyScale", edit.energyScale());
			if (!edit.spatialMotifEnabled()) {
				obj.addProperty("spatialMotifEnabled", false);
			}
			if (edit.spatialMotifIdOverride() != null) {
				obj.addProperty("spatialMotifIdOverride", edit.spatialMotifIdOverride().name());
			}
			if (edit.grammarTriggerIntervalOverride() != null) {
				obj.addProperty("grammarTriggerIntervalOverride", edit.grammarTriggerIntervalOverride());
			}
			if (edit.grammarStaggerStepOverride() != null) {
				obj.addProperty("grammarStaggerStepOverride", edit.grammarStaggerStepOverride());
			}
			if (edit.grammarIntensityCurveOverride() != null) {
				obj.addProperty("grammarIntensityCurveOverride", edit.grammarIntensityCurveOverride());
			}
			if (edit.grammarVariationOverride() != null) {
				obj.addProperty("grammarVariationOverride", edit.grammarVariationOverride());
			}
			arr.add(obj);
		}
		return arr;
	}

	private static List<SectionEditProfile> sectionEditsFromJson(@Nullable JsonElement element) {
		List<SectionEditProfile> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			JsonObject obj = arr.get(i).getAsJsonObject();
			Double density = obj.has("densityThresholdOverride") && !obj.get("densityThresholdOverride").isJsonNull()
				? obj.get("densityThresholdOverride").getAsDouble() : null;
			String animation = obj.has("motionAnimationTypeOverride") && !obj.get("motionAnimationTypeOverride").isJsonNull()
				? obj.get("motionAnimationTypeOverride").getAsString() : null;
			SpatialMotifId motifOverride = null;
			if (obj.has("spatialMotifIdOverride") && !obj.get("spatialMotifIdOverride").isJsonNull()) {
				motifOverride = SpatialMotifId.fromValue(obj.get("spatialMotifIdOverride").getAsString());
			}
			Integer grammarTrigger = obj.has("grammarTriggerIntervalOverride")
				&& !obj.get("grammarTriggerIntervalOverride").isJsonNull()
				? obj.get("grammarTriggerIntervalOverride").getAsInt() : null;
			Double grammarStagger = obj.has("grammarStaggerStepOverride")
				&& !obj.get("grammarStaggerStepOverride").isJsonNull()
				? obj.get("grammarStaggerStepOverride").getAsDouble() : null;
			String grammarIntensity = obj.has("grammarIntensityCurveOverride")
				&& !obj.get("grammarIntensityCurveOverride").isJsonNull()
				? obj.get("grammarIntensityCurveOverride").getAsString() : null;
			String grammarVariation = obj.has("grammarVariationOverride")
				&& !obj.get("grammarVariationOverride").isJsonNull()
				? obj.get("grammarVariationOverride").getAsString() : null;
			out.add(new SectionEditProfile(
				getInt(obj, "sectionIndex", 0),
				getBool(obj, "motionEnabled", true),
				getBool(obj, "cameraEnabled", true),
				getBool(obj, "vfxEnabled", true),
				animation,
				density,
				getDouble(obj, "timeOffsetSeconds", 0.0),
				getFloat(obj, "energyScale", 1f),
				getBool(obj, "spatialMotifEnabled", true),
				motifOverride,
				grammarTrigger,
				grammarStagger,
				grammarIntensity,
				grammarVariation
			));
		}
		return out;
	}

	private static SectionPlanSource parseSectionSource(String raw) {
		if (raw == null || raw.isBlank()) return SectionPlanSource.ANALYZED;
		try {
			return SectionPlanSource.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return SectionPlanSource.ANALYZED;
		}
	}

	private static ChoreographyTimingSnap parseTimingSnap(JsonObject obj, String featureKey) {
		return parseTimingSnap(obj, TimingSnapDefaults.forFeatureKey(featureKey));
	}

	private static ChoreographyTimingSnap parseTimingSnap(JsonObject obj, ChoreographyTimingSnap fallback) {
		if (obj == null || !obj.has("timingSnap") || obj.get("timingSnap").isJsonNull()) {
			return fallback;
		}
		try {
			return ChoreographyTimingSnap.valueOf(obj.get("timingSnap").getAsString().trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return fallback;
		}
	}

	private static String getString(JsonObject obj, String key, String def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsString();
	}

	private static double getDouble(JsonObject obj, String key, double def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsDouble();
	}

	private static float getFloat(JsonObject obj, String key, float def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsFloat();
	}

	private static int getInt(JsonObject obj, String key, int def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsInt();
	}

	private static boolean getBool(JsonObject obj, String key, boolean def) {
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsBoolean();
	}
}
