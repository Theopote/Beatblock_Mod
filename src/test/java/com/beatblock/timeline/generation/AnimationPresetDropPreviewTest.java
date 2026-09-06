package com.beatblock.timeline.generation;

import com.beatblock.test.WithBeatBlockContext;
import com.beatblock.ui.i18n.BBTexts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithBeatBlockContext
class AnimationPresetDropPreviewTest {

	@Test
	void formatsSingleTargetWithResolvedName() {
		var targets = new AnimationDropTargetResolver.Result(
			AnimationDropTargetResolver.Mode.SINGLE,
			List.of("building-a")
		);

		var lines = AnimationPresetDropPreview.format(
			"Pulse",
			targets,
			12.5,
			id -> Map.of("building-a", "Building_A").get(id)
		);

		assertEquals("Pulse", lines.presetLine());
		assertEquals(
			BBTexts.get("beatblock.animation_library.drop_preview.target", "Building_A"),
			lines.targetLine()
		);
		assertEquals(
			BBTexts.get("beatblock.animation_library.drop_preview.time", "12.50"),
			lines.timeLine()
		);
	}

	@Test
	void formatsMultiTargetCount() {
		var targets = new AnimationDropTargetResolver.Result(
			AnimationDropTargetResolver.Mode.MULTI,
			List.of("a", "b", "c", "d")
		);

		var lines = AnimationPresetDropPreview.format("Pulse", targets, 3.0, id -> id);

		assertEquals("Pulse", lines.presetLine());
		assertEquals(
			BBTexts.get("beatblock.animation_library.drop_preview.target_multi", 4),
			lines.targetLine()
		);
		assertEquals(
			BBTexts.get("beatblock.animation_library.drop_preview.time", "3.00"),
			lines.timeLine()
		);
	}

	@Test
	void formatsUnboundTarget() {
		var targets = new AnimationDropTargetResolver.Result(
			AnimationDropTargetResolver.Mode.UNBOUND,
			List.of()
		);

		var lines = AnimationPresetDropPreview.format("Pulse", targets, 0.0, null);

		assertEquals("Pulse", lines.presetLine());
		assertEquals(
			BBTexts.get("beatblock.animation_library.drop_preview.target_unbound"),
			lines.targetLine()
		);
	}

	@Test
	void fallsBackToIdWhenNameMissing() {
		var targets = new AnimationDropTargetResolver.Result(
			AnimationDropTargetResolver.Mode.SINGLE,
			List.of("solo-id")
		);

		var lines = AnimationPresetDropPreview.format("Meteor", targets, 1.25, id -> null);

		assertEquals(
			BBTexts.get("beatblock.animation_library.drop_preview.target", "solo-id"),
			lines.targetLine()
		);
	}
}
