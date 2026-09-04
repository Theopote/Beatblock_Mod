package com.beatblock.automap.choreography.grammar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 将 {@link ChoreographyPhrase#trigger()} 解析为一系列 {@link TriggerInstance}。
 */
public final class PhraseTriggerResolver {

	private PhraseTriggerResolver() {}

	public static List<TriggerInstance> resolve(ChoreographyPhrase phrase, PhraseTriggerContext context) {
		if (phrase == null || phrase.trigger() == null) return List.of();
		PhraseTriggerContext resolved = context != null ? context : PhraseTriggerContext.empty();
		return switch (phrase.trigger()) {
			case TriggerSpec.OnFeature onFeature -> resolveOnFeature(onFeature, resolved);
			case TriggerSpec.EveryNBeats everyNBeats -> resolveEveryNBeats(everyNBeats, resolved);
			case TriggerSpec.FirstFeature firstFeature -> resolveFirstFeature(firstFeature, resolved);
		};
	}

	private static List<TriggerInstance> resolveOnFeature(
		TriggerSpec.OnFeature trigger,
		PhraseTriggerContext context
	) {
		List<FeatureEventRef> matches = matchingFeatures(
			trigger.normalizedFeatureKey(),
			eventsInActiveRange(context)
		);
		List<TriggerInstance> out = new ArrayList<>();
		int index = 0;
		for (FeatureEventRef event : matches) {
			if (event.energy() < trigger.minEnergy()) continue;
			out.add(new TriggerInstance(event.timeSeconds(), event.energy(), index++));
		}
		return List.copyOf(out);
	}

	private static List<TriggerInstance> resolveFirstFeature(
		TriggerSpec.FirstFeature trigger,
		PhraseTriggerContext context
	) {
		List<FeatureEventRef> matches = matchingFeatures(
			trigger.normalizedFeatureKey(),
			eventsInActiveRange(context)
		);
		for (FeatureEventRef event : matches) {
			if (event.energy() < trigger.minEnergy()) continue;
			return List.of(new TriggerInstance(event.timeSeconds(), event.energy(), 0));
		}
		return List.of();
	}

	private static List<TriggerInstance> resolveEveryNBeats(
		TriggerSpec.EveryNBeats trigger,
		PhraseTriggerContext context
	) {
		List<FeatureEventRef> matches = matchingFeatures(
			trigger.anchorFeatureKey(),
			eventsInActiveRange(context)
		);
		List<TriggerInstance> out = new ArrayList<>();
		for (int i = 0; i < matches.size(); i += trigger.interval()) {
			FeatureEventRef event = matches.get(i);
			out.add(new TriggerInstance(event.timeSeconds(), event.energy(), out.size()));
		}
		return List.copyOf(out);
	}

	private static List<FeatureEventRef> eventsInActiveRange(PhraseTriggerContext context) {
		return context.featureEvents().stream()
			.filter(event -> context.containsTime(event.timeSeconds()))
			.toList();
	}

	private static List<FeatureEventRef> matchingFeatures(String featureKey, List<FeatureEventRef> events) {
		String normalized = normalize(featureKey);
		return events.stream()
			.filter(event -> matchesFeature(normalized, event.featureKey()))
			.sorted(Comparator.comparingDouble(FeatureEventRef::timeSeconds))
			.toList();
	}

	private static boolean matchesFeature(String normalizedKey, String eventKey) {
		if (normalizedKey.isEmpty()) return true;
		String normalizedEvent = normalize(eventKey);
		return normalizedKey.equals(normalizedEvent)
			|| normalizedEvent.contains(normalizedKey)
			|| normalizedKey.contains(normalizedEvent);
	}

	private static String normalize(String key) {
		if (key == null) return "";
		return key.trim().toLowerCase(Locale.ROOT);
	}
}
