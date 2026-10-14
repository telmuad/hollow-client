package dev.telmuad.hollowclient.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** All modules, in registration order. Adding a feature = write a Module subclass and register it here. */
public final class ModuleRegistry {
	private final Map<String, Module> modules = new LinkedHashMap<>();
	private Consumer<Module> changeListener = m -> {
	};

	public <M extends Module> M register(M module) {
		if (modules.containsKey(module.id())) throw new IllegalArgumentException("Duplicate module id " + module.id());
		modules.put(module.id(), module);
		module.setChangeListener(m -> changeListener.accept(m));
		return module;
	}

	public void setChangeListener(Consumer<Module> listener) {
		this.changeListener = listener == null ? m -> {
		} : listener;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(new ArrayList<>(modules.values()));
	}

	public List<Module> byCategory(Category category) {
		List<Module> out = new ArrayList<>();
		for (Module m : modules.values()) if (m.category() == category) out.add(m);
		return out;
	}

	public Optional<Module> get(String id) {
		return Optional.ofNullable(modules.get(id));
	}

	@SuppressWarnings("unchecked")
	public <M extends Module> Optional<M> get(Class<M> type) {
		for (Module m : modules.values()) if (type.isInstance(m)) return Optional.of((M) m);
		return Optional.empty();
	}
}
