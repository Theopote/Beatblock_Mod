package com.beatblock.ui.presenter;

import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartWizardPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private QuickStartWizardPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		editor = new TimelineEditor(timeline);
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(() -> null),
			new ToolPanelPresenter(() -> null, () -> null, () -> null, null),
			new RhythmDropPanelPresenter(() -> null, () -> timeline, () -> editor, () -> null),
			() -> timeline,
			() -> editor
		);
		AudioAssetManager.getInstance().getAssets().forEach(asset ->
			AudioAssetManager.getInstance().remove(asset.getId()));
	}

	@Test
	void importMusicRejectsEmptyPath() {
		var result = presenter.importMusic("  ");
		assertFalse(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
	}

	@Test
	void importMusicRejectsUnsupportedExtension() {
		var result = presenter.importMusic("C:/music/track.txt");
		assertFalse(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.IMPORT, presenter.step());
	}

	@Test
	void importMusicAcceptsMp3AndStartsAnalysis(@TempDir Path tempDir) throws Exception {
		Path mp3 = tempDir.resolve("demo.mp3");
		Files.write(mp3, new byte[] {0x49, 0x44, 0x33, 0x03});

		var result = presenter.importMusic(mp3.toString());
		assertTrue(result.ok());
		assertEquals(QuickStartWizardPresenter.Step.CHOOSE_TYPE, presenter.step());
		assertEquals(mp3.toAbsolutePath().normalize().toString(), timeline.getMetadata("audioPath"));
		assertEquals(1, AudioAssetManager.getInstance().getAssets().size());
	}
}
