package com.beatblock.client.camera;

import com.beatblock.automap.camera.CameraSubject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraCreatorVisualizationTest {

	@BeforeEach
	@AfterEach
	void reset() {
		CameraCreatorVisualization.resetForTests();
	}

	@Test
	void defaultsEnablePathOnly() {
		assertTrue(CameraCreatorVisualization.showCameraPath());
		assertFalse(CameraCreatorVisualization.showFrustum());
		assertFalse(CameraCreatorVisualization.showSubjectBounds());
		assertEquals(CameraSubject.allStageObjects(), CameraCreatorVisualization.subjectForBounds());
	}

	@Test
	void togglesAndSubjectAreSessionScoped() {
		CameraCreatorVisualization.setShowCameraPath(false);
		CameraCreatorVisualization.setShowFrustum(true);
		CameraCreatorVisualization.setShowSubjectBounds(true);
		CameraCreatorVisualization.setSubjectForBounds(CameraSubject.stageObject("solo"));

		assertFalse(CameraCreatorVisualization.showCameraPath());
		assertTrue(CameraCreatorVisualization.showFrustum());
		assertTrue(CameraCreatorVisualization.showSubjectBounds());
		assertEquals(CameraSubject.stageObject("solo"), CameraCreatorVisualization.subjectForBounds());
	}
}
