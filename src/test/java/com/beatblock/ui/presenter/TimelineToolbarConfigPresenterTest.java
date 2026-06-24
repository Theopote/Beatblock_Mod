package com.beatblock.ui.presenter;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.binding.AnimationBindingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineToolbarConfigPresenterTest {

	@TempDir
	Path tempDir;

	private Timeline timeline;
	private Path configPath;
	private TimelineToolbarConfigPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		configPath = tempDir.resolve("ui.json");
		presenter = new TimelineToolbarConfigPresenter(() -> timeline, () -> configPath);
	}

	@Test
	void demucsSeparationInactiveByDefault() {
		assertFalse(presenter.isDemucsSeparationActive());
	}

	@Test
	void demucsSeparationActiveWhenMetadataSet() {
		timeline.setMetadata("separationMode", "demucs");
		assertTrue(presenter.isDemucsSeparationActive());
	}

	@Test
	void writeAndReadDemucsPreset() {
		presenter.writeDemucsPreset("drive");
		assertEquals("drive", presenter.readDemucsPreset());
	}

	@Test
	void writeAndReadClipGenerationMode() {
		presenter.writeClipGenerationMode("trigger");
		assertEquals("trigger", presenter.readClipGenerationMode());
	}

	@Test
	void writeAndReadActionRollbackMode() {
		presenter.writeActionRollbackMode("persistent");
		assertEquals("persistent", presenter.readActionRollbackMode());
		assertEquals("Action: Persistent", presenter.actionRollbackViewState().statusLabel());
	}

	@Test
	void globalScalesRoundTrip() {
		presenter.writeGlobalScales(1.2f, 0.9f, 1.5f);
		var scales = presenter.readGlobalScales();
		assertEquals(1.2, scales.durationScale(), 1e-6);
		assertEquals(0.9, scales.energyScale(), 1e-6);
		assertEquals(1.5, scales.gapScale(), 1e-6);
	}

	@Test
	void loadsDemucsPresetFromUiJson() throws Exception {
		Files.writeString(configPath, """
			{"demucsMapping":{"preset":"detail","clipGenerationMode":"sustain"}}
			""");
		presenter.ensureDemucsMappingConfigLoaded();
		assertEquals("detail", presenter.readDemucsPreset());
		assertEquals("sustain", presenter.readClipGenerationMode());
	}

	@Test
	void indexHelpersFindKnownValues() {
		assertEquals(1, TimelineToolbarConfigPresenter.indexOfDemucsPresetValue("balanced"));
		assertEquals(0, TimelineToolbarConfigPresenter.indexOfClipGenerationMode("mixed"));
		assertEquals(1, TimelineToolbarConfigPresenter.indexOfActionRollbackValue("persistent"));
	}
}
