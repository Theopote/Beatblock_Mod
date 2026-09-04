package com.beatblock.automap.choreography.grammar;

import com.beatblock.automap.choreography.ChoreographyTimingSnap;
import com.beatblock.automap.choreography.MotifAxis;
import com.beatblock.automap.choreography.SpatialMotifId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@link ChoreographyPhrase} ↔ JSON 持久化（Phase 1）。 */
public final class ChoreographyPhrasePersistence {

	private ChoreographyPhrasePersistence() {}

	public static JsonArray toJson(List<ChoreographyPhrase> phrases) {
		JsonArray arr = new JsonArray();
		if (phrases == null) return arr;
		for (ChoreographyPhrase phrase : phrases) {
			arr.add(phraseToJson(phrase));
		}
		return arr;
	}

	public static List<ChoreographyPhrase> fromJson(@Nullable JsonElement element) {
		if (element == null || !element.isJsonArray()) return List.of();
		JsonArray arr = element.getAsJsonArray();
		List<ChoreographyPhrase> out = new ArrayList<>(arr.size());
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			out.add(phraseFromJson(arr.get(i).getAsJsonObject()));
		}
		return out;
	}

	private static JsonObject phraseToJson(ChoreographyPhrase phrase) {
		JsonObject root = new JsonObject();
		root.add("trigger", triggerToJson(phrase.trigger()));
		JsonArray targets = new JsonArray();
		for (String id : phrase.targets().objectIds()) {
			targets.add(id);
		}
		root.add("targets", targets);
		root.add("spatial", spatialToJson(phrase.spatial()));
		root.addProperty("motionPreset", phrase.motion().presetId());
		root.addProperty("motionDurationSeconds", phrase.motion().durationSeconds());
		root.addProperty("useEnergyForHeight", phrase.motion().useEnergyForHeight());
		root.addProperty("heightMultiplier", phrase.motion().heightMultiplier());
		if (phrase.timing() instanceof TimingPatternSpec.Stagger stagger) {
			root.addProperty("staggerSeconds", stagger.stepSeconds());
		}
		root.addProperty("intensityStart", phrase.intensity().startEnergy());
		root.addProperty("intensityEnd", phrase.intensity().endEnergy());
		root.addProperty("intensityCurve", phrase.intensity().curve().name());
		root.addProperty("variationKind", phrase.variation().kind().name());
		root.addProperty("variationAmount", phrase.variation().amount());
		root.addProperty("sectionIndex", phrase.sectionIndex());
		if (phrase.timingSnap() != ChoreographyTimingSnap.BAR) {
			root.addProperty("timingSnap", phrase.timingSnap().name());
		}
		if (phrase.layer() != com.beatblock.automap.choreography.ChoreographyLayer.PHRASE) {
			root.addProperty("layer", phrase.layer().name());
		}
		return root;
	}

	private static ChoreographyPhrase phraseFromJson(JsonObject root) {
		TriggerSpec trigger = triggerFromJson(root.getAsJsonObject("trigger"));
		List<String> targets = new ArrayList<>();
		if (root.has("targets") && root.get("targets").isJsonArray()) {
			JsonArray arr = root.getAsJsonArray("targets");
			for (int i = 0; i < arr.size(); i++) {
				targets.add(arr.get(i).getAsString());
			}
		}
		SpatialPatternSpec spatial = root.has("spatial")
			? spatialFromJson(root.getAsJsonObject("spatial"))
			: SpatialPatternSpec.leftToRight();
		MotionPresetSpec motion = new MotionPresetSpec(
			getString(root, "motionPreset", "bounce"),
			getDouble(root, "motionDurationSeconds", 0.5),
			getBool(root, "useEnergyForHeight", true),
			getFloat(root, "heightMultiplier", 4f)
		);
		TimingPatternSpec timing = root.has("staggerSeconds")
			? TimingPatternSpec.stagger(getDouble(root, "staggerSeconds", 0.08))
			: new TimingPatternSpec.Simultaneous();
		IntensityEnvelope intensity = new IntensityEnvelope(
			getFloat(root, "intensityStart", 0.8f),
			getFloat(root, "intensityEnd", 0.8f),
			parseCurve(getString(root, "intensityCurve", "FLAT"))
		);
		VariationSpec variation = new VariationSpec(
			parseVariation(getString(root, "variationKind", "NONE")),
			getFloat(root, "variationAmount", 0f)
		);
		ChoreographyTimingSnap snap = parseTimingSnap(root);
		com.beatblock.automap.choreography.ChoreographyLayer layer = parseLayer(root);
		return new ChoreographyPhrase(
			trigger,
			new TargetSet(targets),
			spatial,
			motion,
			timing,
			intensity,
			variation,
			getInt(root, "sectionIndex", -1),
			snap,
			layer
		);
	}

	private static JsonObject triggerToJson(TriggerSpec trigger) {
		JsonObject obj = new JsonObject();
		return switch (trigger) {
			case TriggerSpec.OnFeature onFeature -> {
				obj.addProperty("type", "on_feature");
				obj.addProperty("featureKey", onFeature.normalizedFeatureKey());
				obj.addProperty("minEnergy", onFeature.minEnergy());
				yield obj;
			}
			case TriggerSpec.EveryNBeats everyNBeats -> {
				obj.addProperty("type", "every_n_beats");
				obj.addProperty("featureKey", everyNBeats.anchorFeatureKey());
				obj.addProperty("interval", everyNBeats.interval());
				yield obj;
			}
			case TriggerSpec.FirstFeature firstFeature -> {
				obj.addProperty("type", "first_feature");
				obj.addProperty("featureKey", firstFeature.normalizedFeatureKey());
				obj.addProperty("minEnergy", firstFeature.minEnergy());
				yield obj;
			}
		};
	}

	private static TriggerSpec triggerFromJson(@Nullable JsonObject obj) {
		if (obj == null) return new TriggerSpec.OnFeature("low");
		String type = getString(obj, "type", "on_feature");
		String featureKey = getString(obj, "featureKey", "kick");
		if ("every_n_beats".equalsIgnoreCase(type)) {
			return new TriggerSpec.EveryNBeats(getInt(obj, "interval", 4), featureKey);
		}
		if ("first_feature".equalsIgnoreCase(type)) {
			return new TriggerSpec.FirstFeature(featureKey, getFloat(obj, "minEnergy", 0f));
		}
		return new TriggerSpec.OnFeature(featureKey, getFloat(obj, "minEnergy", 0f));
	}

	private static JsonObject spatialToJson(SpatialPatternSpec spatial) {
		SpatialPatternSpec resolved = spatial != null ? spatial : SpatialPatternSpec.leftToRight();
		JsonObject obj = new JsonObject();
		obj.addProperty("pattern", resolved.pattern().name());
		obj.addProperty("axis", resolved.axis().name());
		if (resolved.layoutKind() != null) {
			obj.addProperty("layoutKind", resolved.layoutKind().name());
		}
		return obj;
	}

	private static SpatialPatternSpec spatialFromJson(@Nullable JsonObject obj) {
		if (obj == null) return SpatialPatternSpec.leftToRight();
		SpatialMotifId pattern = SpatialMotifId.fromValue(getString(obj, "pattern", "CASCADE"));
		MotifAxis axis = MotifAxis.fromValue(getString(obj, "axis", "X"));
		String layout = getString(obj, "layoutKind", "");
		SpatialPatternSpec.SpatialLayoutKind layoutKind = layout.isBlank()
			? null
			: SpatialPatternSpec.SpatialLayoutKind.valueOf(layout);
		return new SpatialPatternSpec(pattern, axis, layoutKind);
	}

	private static IntensityEnvelope.EnvelopeCurve parseCurve(String raw) {
		try {
			return IntensityEnvelope.EnvelopeCurve.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return IntensityEnvelope.EnvelopeCurve.FLAT;
		}
	}

	private static VariationSpec.VariationKind parseVariation(String raw) {
		try {
			return VariationSpec.VariationKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return VariationSpec.VariationKind.NONE;
		}
	}

	private static ChoreographyTimingSnap parseTimingSnap(JsonObject root) {
		try {
			return ChoreographyTimingSnap.valueOf(getString(root, "timingSnap", "BAR").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return ChoreographyTimingSnap.BAR;
		}
	}

	private static com.beatblock.automap.choreography.ChoreographyLayer parseLayer(JsonObject root) {
		String raw = getString(root, "layer", "PHRASE");
		try {
			com.beatblock.automap.choreography.ChoreographyLayer layer =
				com.beatblock.automap.choreography.ChoreographyLayer.valueOf(raw.trim().toUpperCase(Locale.ROOT));
			return layer == com.beatblock.automap.choreography.ChoreographyLayer.HERO
				? com.beatblock.automap.choreography.ChoreographyLayer.HERO
				: com.beatblock.automap.choreography.ChoreographyLayer.PHRASE;
		} catch (IllegalArgumentException ex) {
			return com.beatblock.automap.choreography.ChoreographyLayer.PHRASE;
		}
	}

	private static String getString(JsonObject obj, String key, String def) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsString();
	}

	private static double getDouble(JsonObject obj, String key, double def) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsDouble();
	}

	private static float getFloat(JsonObject obj, String key, float def) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsFloat();
	}

	private static int getInt(JsonObject obj, String key, int def) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsInt();
	}

	private static boolean getBool(JsonObject obj, String key, boolean def) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
		return obj.get(key).getAsBoolean();
	}
}
