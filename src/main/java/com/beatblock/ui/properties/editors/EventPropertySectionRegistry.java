package com.beatblock.ui.properties.editors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Registry of {@link EventPropertySection} instances for the animation property editor.
 * <p>
 * Built-in sections are registered by default; plugins may call {@link #register} at runtime.
 */
public final class EventPropertySectionRegistry {

	private final List<EventPropertySection> sections = new ArrayList<>();

	public EventPropertySectionRegistry() {
		registerDefaults();
	}

	/** Create a registry with only the built-in BeatBlock sections. */
	public static EventPropertySectionRegistry createDefault() {
		return new EventPropertySectionRegistry();
	}

	private void registerDefaults() {
		// BASIC
		register(new EventTimingSection());
		register(new EventBindingSection());
		register(new PresetSection());
		register(new VfxSection());
		register(new TargetSection());
		// SPATIAL
		register(new WorldTrajectorySection());
		register(new SpatialDispatchSection());
		// ADVANCED
		register(new StepSequenceSection());
		register(new PhaseAnimationSection());
		// INFO
		register(new EventDiagnosticsSection());
	}

	/**
	 * Register a section (plugin extension point).
	 * Duplicate class instances are allowed; order follows registration + {@link EventPropertySection#order()}.
	 */
	public synchronized void register(EventPropertySection section) {
		Objects.requireNonNull(section, "section");
		sections.add(section);
	}

	/** Snapshot of all registered sections, sorted by tab then order. */
	public synchronized List<EventPropertySection> all() {
		return sections.stream()
			.sorted(Comparator
				.comparingInt((EventPropertySection s) -> s.tab().ordinal())
				.thenComparingInt(EventPropertySection::order))
			.toList();
	}

	/** Sections for a given tab, sorted by {@link EventPropertySection#order()}. */
	public List<EventPropertySection> forTab(EventPropertySection.Tab tab) {
		Objects.requireNonNull(tab, "tab");
		return all().stream()
			.filter(s -> s.tab() == tab)
			.toList();
	}

	/** Render all supported sections for a tab. */
	public void renderTab(EventPropertySection.Tab tab, EventEditContext context) {
		for (EventPropertySection section : forTab(tab)) {
			if (section.supports(context)) {
				section.render(context);
			}
		}
	}
}
