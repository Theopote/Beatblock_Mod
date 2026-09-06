package com.beatblock.timeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerEditPolicyTest {

	@Test
	void generatedSectionRequiresConfirmForTypeAndDelete() {
		TimelineMarker marker = TimelineMarker.audioAnalysisSection(0, "SECTION X");
		assertTrue(MarkerEditPolicy.requiresStructuralConfirm(
			marker, MarkerEditPolicy.StructuralAction.DELETE, null));
		assertTrue(MarkerEditPolicy.requiresStructuralConfirm(
			marker, MarkerEditPolicy.StructuralAction.CHANGE_TYPE, MarkerType.FX));
		assertFalse(MarkerEditPolicy.allowsMutation(
			marker, MarkerEditPolicy.StructuralAction.DELETE, null, false));
		assertTrue(MarkerEditPolicy.allowsMutation(
			marker, MarkerEditPolicy.StructuralAction.DELETE, null, true));
	}

	@Test
	void lockedBlocksAllMutations() {
		TimelineMarker locked = TimelineMarker.audioAnalysisSection(0, "SECTION X")
			.withEditState(MarkerEditState.LOCKED);
		assertTrue(MarkerEditPolicy.isLocked(locked));
		assertFalse(MarkerEditPolicy.allowsContentEdit(locked));
		assertFalse(MarkerEditPolicy.allowsMutation(
			locked, MarkerEditPolicy.StructuralAction.DELETE, null, true));
	}

	@Test
	void onlyGeneratedAnalysisSectionsAreReplaceable() {
		TimelineMarker generated = TimelineMarker.audioAnalysisSection(0, "SECTION A");
		TimelineMarker edited = generated.withFields(1, "SECTION A", MarkerType.SECTION, true);
		TimelineMarker manual = TimelineMarker.manual(0, "SECTION B", MarkerType.SECTION);
		assertTrue(MarkerEditPolicy.isReplaceableByAudioAnalysis(generated));
		assertFalse(MarkerEditPolicy.isReplaceableByAudioAnalysis(edited));
		assertFalse(MarkerEditPolicy.isReplaceableByAudioAnalysis(manual));
	}
}
