package com.beatblock.timeline.editing;

import com.beatblock.automap.vfx.GlobalEffectKind;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GlobalEventPropertiesEditorTest {

	@Test
	void buildUpdatedSnapshotPreservesTypedPayloadFields() {
		Map<String, Object> params = GlobalEventPayloadCodec.encode(
			new GlobalEventPayload.ScreenTint("Blue", 0.4, 0.2f, 0.3f, 1f, 3.0));
		var result = GlobalEventPropertiesEditor.buildUpdatedSnapshot(
			2.5,
			new GlobalEventPayload.ScreenTint("Blue", 0.55, 0.1f, 0.2f, 0.9f, 4.0),
			2.0,
			3.0,
			Map.of()
		);
		assertInstanceOf(GlobalEventPropertiesEditor.Result.Ok.class, result);
		var snapshot = ((GlobalEventPropertiesEditor.Result.Ok) result).snapshot();
		GlobalEventPayload decoded = GlobalEventPayloadCodec.decode(snapshot.parameters());
		assertInstanceOf(GlobalEventPayload.ScreenTint.class, decoded);
		assertEquals(0.55, ((GlobalEventPayload.ScreenTint) decoded).intensity(), 1e-6);
		assertEquals(4.0, ((GlobalEventPayload.ScreenTint) decoded).durationSeconds(), 1e-6);
	}

	@Test
	void buildPayloadFormSnapshotDecodesKind() {
		var form = GlobalEventPropertiesEditor.buildPayloadFormSnapshot(
			1.0,
			GlobalEventPayloadCodec.encode(GlobalEffectKind.WEATHER.defaultPayload("Rain")));
		assertEquals(GlobalEffectKind.WEATHER, form.kind());
		assertInstanceOf(GlobalEventPayload.LocalVisualWeather.class, form.payload());
	}
}
