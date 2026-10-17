package dev.telmuad.hollowclient.skyblock.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Typed SkyBlock events. Listeners run on the thread that posts (the client thread); one failing listener doesn't
 * stop the others. The last {@value #LOG_SIZE} events are kept for the Hub's event log. Plain Java.
 */
public final class EventBus {
	public static final int LOG_SIZE = 200;

	/** A posted event with when it happened. */
	public record Logged(long time, SbEvent event) {
	}

	private final Map<Class<?>, List<Consumer<? super SbEvent>>> listeners = new ConcurrentHashMap<>();
	private final List<Consumer<? super SbEvent>> any = new CopyOnWriteArrayList<>();
	private final Deque<Logged> log = new ArrayDeque<>();
	private Consumer<Throwable> errors = Throwable::printStackTrace;

	@SuppressWarnings("unchecked")
	public <T extends SbEvent> void on(Class<T> type, Consumer<? super T> listener) {
		listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(e -> listener.accept((T) e));
	}

	/** Every event, whatever its type. */
	public void onAny(Consumer<? super SbEvent> listener) {
		any.add(listener);
	}

	public void setErrorSink(Consumer<Throwable> sink) {
		this.errors = sink == null ? t -> {
		} : sink;
	}

	public void post(SbEvent event) {
		synchronized (log) {
			log.addFirst(new Logged(System.currentTimeMillis(), event));
			while (log.size() > LOG_SIZE) log.removeLast();
		}
		List<Consumer<? super SbEvent>> typed = listeners.get(event.getClass());
		if (typed != null) for (Consumer<? super SbEvent> l : typed) run(l, event);
		for (Consumer<? super SbEvent> l : any) run(l, event);
	}

	private void run(Consumer<? super SbEvent> l, SbEvent e) {
		try {
			l.accept(e);
		} catch (RuntimeException ex) {
			errors.accept(ex);
		}
	}

	/** Newest first. */
	public List<Logged> recent() {
		synchronized (log) {
			return new ArrayList<>(log);
		}
	}
}
