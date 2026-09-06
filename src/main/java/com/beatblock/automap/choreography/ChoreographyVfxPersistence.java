package com.beatblock.automap.choreography;

import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.camera.CameraSubjectKind;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Choreography VFX ↔ JSON（.osc 编舞计划持久化）。 */
public final class ChoreographyVfxPersistence {

	private ChoreographyVfxPersistence() {}

	public static JsonArray toJson(List<ChoreographyVfx> phrases) {
		JsonArray arr = new JsonArray();
		if (phrases == null) return arr;
		for (ChoreographyVfx phrase : phrases) {
			arr.add(phraseToJson(phrase));
		}
		return arr;
	}

	public static List<ChoreographyVfx> fromJson(@Nullable JsonElement element) {
		List<ChoreographyVfx> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) return out;
		JsonArray arr = element.getAsJsonArray();
		for (int i = 0; i < arr.size(); i++) {
			if (!arr.get(i).isJsonObject()) continue;
			ChoreographyVfx phrase = phraseFromJson(arr.get(i).getAsJsonObject());
			if (phrase != null) out.add(phrase);
		}
		return out;
	}

	private static JsonObject phraseToJson(ChoreographyVfx phrase) {
		JsonObject obj = new JsonObject();
		obj.addProperty("timeSeconds", phrase.timeSeconds());
		obj.addProperty("sectionIndex", phrase.sectionIndex());
		return switch (phrase) {
			case ChoreographyVfx.ParticleBurst particle -> {
				obj.addProperty("kind", "PARTICLE_BURST");
				obj.addProperty("name", particle.name());
				obj.addProperty("particleType", particle.particleType());
				writeSubject(obj, particle.target());
				obj.addProperty("count", particle.count());
				obj.addProperty("spread", particle.spread());
				obj.addProperty("speed", particle.speed());
				yield obj;
			}
			case ChoreographyVfx.ScreenFlash flash -> {
				obj.addProperty("kind", "SCREEN_FLASH");
				obj.addProperty("name", flash.name());
				obj.addProperty("r", flash.r());
				obj.addProperty("g", flash.g());
				obj.addProperty("b", flash.b());
				obj.addProperty("durationSeconds", flash.durationSeconds());
				yield obj;
			}
			case ChoreographyVfx.ScreenTint tint -> {
				obj.addProperty("kind", "SCREEN_TINT");
				obj.addProperty("name", tint.name());
				obj.addProperty("intensity", tint.intensity());
				obj.addProperty("r", tint.r());
				obj.addProperty("g", tint.g());
				obj.addProperty("b", tint.b());
				obj.addProperty("durationSeconds", tint.durationSeconds());
				yield obj;
			}
			case ChoreographyVfx.EnvironmentLighting lighting -> {
				obj.addProperty("kind", "ENVIRONMENT_LIGHTING");
				obj.addProperty("name", lighting.name());
				obj.addProperty("intensity", lighting.intensity());
				obj.addProperty("r", lighting.r());
				obj.addProperty("g", lighting.g());
				obj.addProperty("b", lighting.b());
				obj.addProperty("transitionSeconds", lighting.transitionSeconds());
				yield obj;
			}
			case ChoreographyVfx.AudioAccent accent -> {
				obj.addProperty("kind", "AUDIO_ACCENT");
				obj.addProperty("name", accent.name());
				obj.addProperty("channel", accent.channel());
				obj.addProperty("volume", accent.volume());
				obj.addProperty("fadeSeconds", accent.fadeSeconds());
				yield obj;
			}
		};
	}

	private static @Nullable ChoreographyVfx phraseFromJson(JsonObject obj) {
		if (obj.has("vfxKind") && !obj.has("kind")) {
			return ChoreographyVfxFactory.fromLegacyVfxKind(
				getDouble(obj, "timeSeconds", 0.0),
				getString(obj, "vfxKind", ""),
				getInt(obj, "sectionIndex", -1)
			);
		}
		String kind = getString(obj, "kind", "").toUpperCase();
		double time = getDouble(obj, "timeSeconds", 0.0);
		int sectionIndex = getInt(obj, "sectionIndex", -1);
		return switch (kind) {
			case "PARTICLE_BURST" -> new ChoreographyVfx.ParticleBurst(
				time,
				getString(obj, "name", ""),
				getString(obj, "particleType", "minecraft:poof"),
				readSubject(obj),
				getInt(obj, "count", 8),
				getDouble(obj, "spread", 0.5),
				getDouble(obj, "speed", 0.04),
				sectionIndex
			);
			case "SCREEN_FLASH" -> new ChoreographyVfx.ScreenFlash(
				time,
				getString(obj, "name", ""),
				(float) getDouble(obj, "r", 1.0),
				(float) getDouble(obj, "g", 1.0),
				(float) getDouble(obj, "b", 1.0),
				getDouble(obj, "durationSeconds", 0.1),
				sectionIndex
			);
			case "SCREEN_TINT" -> new ChoreographyVfx.ScreenTint(
				time,
				getString(obj, "name", ""),
				getDouble(obj, "intensity", 1.0),
				(float) getDouble(obj, "r", 1.0),
				(float) getDouble(obj, "g", 1.0),
				(float) getDouble(obj, "b", 1.0),
				getDouble(obj, "durationSeconds", 0.0),
				sectionIndex
			);
			case "ENVIRONMENT_LIGHTING" -> new ChoreographyVfx.EnvironmentLighting(
				time,
				getString(obj, "name", ""),
				getDouble(obj, "intensity", 1.0),
				(float) getDouble(obj, "r", 1.0),
				(float) getDouble(obj, "g", 1.0),
				(float) getDouble(obj, "b", 1.0),
				obj.has("transitionSeconds")
					? getDouble(obj, "transitionSeconds", 0.0)
					: getDouble(obj, "durationSeconds", 0.0),
				sectionIndex
			);
			case "AUDIO_ACCENT" -> new ChoreographyVfx.AudioAccent(
				time,
				getString(obj, "name", ""),
				getString(obj, "channel", "master"),
				(float) getDouble(obj, "volume", 1.0),
				getDouble(obj, "fadeSeconds", 0.0),
				sectionIndex
			);
			default -> null;
		};
	}

	private static void writeSubject(JsonObject obj, CameraSubject target) {
		obj.addProperty("subjectKind", target.kind().name());
		if (!target.refId().isBlank()) obj.addProperty("subjectRef", target.refId());
		if (target.kind() == CameraSubjectKind.WORLD_POSITION) {
			obj.addProperty("subjectX", target.x());
			obj.addProperty("subjectY", target.y());
			obj.addProperty("subjectZ", target.z());
		}
	}

	private static CameraSubject readSubject(JsonObject obj) {
		String kindName = getString(obj, "subjectKind", "");
		if (kindName.isBlank()) return CameraSubject.allStageObjects();
		try {
			return switch (CameraSubjectKind.valueOf(kindName)) {
				case STAGE_OBJECT -> CameraSubject.stageObject(getString(obj, "subjectRef", ""));
				case STAGE_GROUP -> CameraSubject.stageGroup(getString(obj, "subjectRef", ""));
				case BUILD_LAYER -> CameraSubject.buildLayer(getString(obj, "subjectRef", ""));
				case ANIMATED_TARGET -> CameraSubject.animatedTarget(getString(obj, "subjectRef", ""));
				case WORLD_POSITION -> CameraSubject.worldPosition(
					getDouble(obj, "subjectX", 0.0),
					getDouble(obj, "subjectY", 64.0),
					getDouble(obj, "subjectZ", 0.0)
				);
				case ALL_STAGE_OBJECTS -> CameraSubject.allStageObjects();
			};
		} catch (IllegalArgumentException ex) {
			return CameraSubject.allStageObjects();
		}
	}

	private static double getDouble(JsonObject obj, String key, double fallback) {
		return obj.has(key) ? obj.get(key).getAsDouble() : fallback;
	}

	private static int getInt(JsonObject obj, String key, int fallback) {
		return obj.has(key) ? obj.get(key).getAsInt() : fallback;
	}

	private static String getString(JsonObject obj, String key, String fallback) {
		return obj.has(key) ? obj.get(key).getAsString() : fallback;
	}
}
