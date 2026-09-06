package com.beatblock.ui.util;

import com.beatblock.timeline.util.MusicalDurationUnit;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.type.ImInt;

/**
 * Duration / time editor: {@code [ amount dragFloat ][ Seconds|Beats|Bars ▼ ]}.
 * Internally always converts to/from timeline seconds using BPM.
 */
public final class MusicalDurationField {

	private final float[] amount = new float[]{0f};
	private final ImInt unitIndex = new ImInt(0);
	private double seconds;

	public void setSeconds(double seconds, double bpm) {
		this.seconds = Math.max(0.0, seconds);
		syncAmountFromSeconds(bpm);
	}

	/** Sets display unit and refreshes the amount from stored seconds. */
	public void setUnit(MusicalDurationUnit unit, double bpm) {
		MusicalDurationUnit resolved = unit != null ? unit : MusicalDurationUnit.SECONDS;
		unitIndex.set(resolved.ordinal());
		syncAmountFromSeconds(bpm);
	}

	public void setFromSeconds(double seconds, MusicalDurationUnit unit, double bpm) {
		this.seconds = Math.max(0.0, seconds);
		setUnit(unit, bpm);
	}

	public double seconds() {
		return seconds;
	}

	public MusicalDurationUnit unit() {
		return unitAt(unitIndex.get());
	}

	/**
	 * Renders the control. Returns true when the user changed amount or unit.
	 */
	public boolean render(String id, String label, double bpm) {
		boolean changed = false;
		MusicalDurationUnit unit = unit();
		float speed = switch (unit) {
			case SECONDS -> 0.05f;
			case BEATS, BARS -> 0.25f;
		};
		float min = unit == MusicalDurationUnit.SECONDS ? 0.05f : 0.0f;

		ImGui.text(label);
		ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
		if (ImGui.dragFloat("##" + id + "Amt", amount, speed, min, 1.0e6f, "%.2f")) {
			seconds = Math.max(0.05, unit.toSeconds(amount[0], bpm));
			changed = true;
		}
		ImGui.sameLine();
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo("##" + id + "Unit", unitIndex, unitLabels())) {
			syncAmountFromSeconds(bpm);
			changed = true;
		}
		if (bpm <= 1e-6 && unit != MusicalDurationUnit.SECONDS && ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.camera.musical_unit.no_bpm_hint",
				(int) MusicalDurationUnit.FALLBACK_BPM));
		}
		return changed;
	}

	private void syncAmountFromSeconds(double bpm) {
		amount[0] = (float) unit().fromSeconds(seconds, bpm);
	}

	private static MusicalDurationUnit unitAt(int index) {
		MusicalDurationUnit[] values = MusicalDurationUnit.values();
		if (index < 0 || index >= values.length) {
			return MusicalDurationUnit.SECONDS;
		}
		return values[index];
	}

	private static String[] unitLabels() {
		return BBTexts.labels(
			"beatblock.camera.musical_unit.seconds",
			"beatblock.camera.musical_unit.beats",
			"beatblock.camera.musical_unit.bars"
		);
	}
}
