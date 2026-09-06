package com.beatblock.ui.eventlibrary;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Event template library: {@code config/beatblock/event_templates.json}.
 * <p>
 * On load failure the on-disk file is never overwritten — see {@link StoreState#LOAD_ERROR}.
 */
public final class EventTemplateStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(EventTemplateStore.class);

	public enum StoreState {
		NOT_LOADED,
		READY,
		LOAD_ERROR
	}

	private static final LinkedHashMap<String, EventTemplate> templates = new LinkedHashMap<>();
	private static StoreState state = StoreState.NOT_LOADED;
	private static String loadErrorDetail = "";

	private EventTemplateStore() {
	}

	public static StoreState state() {
		ensureLoaded();
		return state;
	}

	public static boolean isReady() {
		return state() == StoreState.READY;
	}

	/** Non-empty when {@link #state()} is {@link StoreState#LOAD_ERROR}. */
	public static String loadErrorDetail() {
		ensureLoaded();
		return loadErrorDetail;
	}

	public static List<EventTemplate> all() {
		ensureLoaded();
		return List.copyOf(templates.values());
	}

	public static Optional<EventTemplate> find(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		ensureLoaded();
		return Optional.ofNullable(templates.get(id));
	}

	/**
	 * @return false when library is not {@link StoreState#READY} (including load error — file untouched)
	 */
	public static boolean add(EventTemplate template) {
		if (template == null) {
			return false;
		}
		ensureLoaded();
		if (state != StoreState.READY) {
			LOGGER.warn("Refusing to add event template while store state is {}", state);
			return false;
		}
		templates.put(template.id(), template);
		return save();
	}

	/**
	 * @return false when missing, not ready, or save blocked
	 */
	public static boolean remove(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		ensureLoaded();
		if (state != StoreState.READY) {
			LOGGER.warn("Refusing to remove event template while store state is {}", state);
			return false;
		}
		if (templates.remove(id) == null) {
			return false;
		}
		return save();
	}

	private static void ensureLoaded() {
		if (state != StoreState.NOT_LOADED) {
			return;
		}
		Path path = storePath();
		EventTemplatePersistence.LoadResult result = EventTemplatePersistence.read(path);
		switch (result.status()) {
			case MISSING -> {
				templates.clear();
				loadErrorDetail = "";
				state = StoreState.READY;
			}
			case ERROR -> {
				templates.clear();
				loadErrorDetail = result.errorMessage() != null ? result.errorMessage() : "unknown";
				state = StoreState.LOAD_ERROR;
				LOGGER.warn("Failed to load event templates from {}: {}", path, loadErrorDetail);
			}
			case OK -> {
				templates.clear();
				for (EventTemplate template : result.templates()) {
					if (template != null) {
						templates.put(template.id(), template);
					}
				}
				loadErrorDetail = "";
				state = StoreState.READY;
				if (result.needsRewrite()) {
					if (!save()) {
						LOGGER.warn("Loaded event templates from {} but failed to rewrite as schema v{}",
							path, EventTemplatePersistence.SCHEMA_VERSION);
					}
				}
			}
		}
	}

	private static boolean save() {
		if (state != StoreState.READY) {
			LOGGER.warn("Refusing to save event templates while store state is {}", state);
			return false;
		}
		Path path = storePath();
		try {
			EventTemplatePersistence.writeAtomically(path, List.copyOf(templates.values()));
			return true;
		} catch (Exception e) {
			LOGGER.warn("Failed to save event templates to {}", path, e);
			return false;
		}
	}

	static Path storePath() {
		String overrideDir = System.getProperty("beatblock.test.configDir");
		if (overrideDir != null && !overrideDir.isBlank()) {
			return Path.of(overrideDir).resolve("event_templates.json");
		}
		return FabricLoader.getInstance().getGameDir()
			.resolve("config").resolve("beatblock").resolve("event_templates.json");
	}

	/** Clears in-memory state so unit tests with a fresh configDir start clean. */
	public static void resetForTests() {
		templates.clear();
		state = StoreState.NOT_LOADED;
		loadErrorDetail = "";
	}
}
