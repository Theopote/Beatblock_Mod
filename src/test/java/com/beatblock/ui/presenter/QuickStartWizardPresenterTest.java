package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickStartWizardPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private QuickStartWizardPresenter presenter;
	private final AudioAssetManager manager = AudioAssetManager.getInstance();

	@BeforeEach
	void setUp() {
		var context = BeatBlock.getContext();
		timeline = context.timeline();
		editor = context.timelineEditor();
		manager.bindContext(BeatBlock::getContext);
		presenter = new QuickStartWizardPresenter(
			new AutoMapSettingsPanelPresenter(BeatBlock::getContext),
			PresenterFactories.toolPanelPresenter(context),
			PresenterFactories.rhythmDropPanelPresenter(context),
			() -> timeline,
			() -> editor
		);
	}

	@AfterEach
	void tearDown() {
		for (AudioAsset asset : new ArrayList<>(manager.getAssets())) {
			manager.remove(asset.getId());
		}
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
		assertEquals(1, manager.getAssets().size());
	}
}
