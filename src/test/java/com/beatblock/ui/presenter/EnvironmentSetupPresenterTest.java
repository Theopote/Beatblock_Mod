package com.beatblock.ui.presenter;

import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentSetupPresenterTest {

	@Test
	void isOpenAfterManualOpen() {
		EnvironmentSetupPresenter presenter = new EnvironmentSetupPresenter(new PythonEnvironmentDiagnostics());
		assertFalse(presenter.isOpen());
		presenter.open();
		assertTrue(presenter.isOpen());
	}

	@Test
	void manualOpenStaysOpenAfterClose() {
		EnvironmentSetupPresenter presenter = new EnvironmentSetupPresenter(new PythonEnvironmentDiagnostics());
		presenter.open();
		presenter.close();
		assertFalse(presenter.isOpen());
	}
}
