package dev.telmuad.hollowclient.util;

import java.util.ArrayDeque;

/** Counts presses in a sliding one-second window. Not thread safe; use it from the client thread. */
public final class ClickTracker {
	public static final long WINDOW_MS = 1000;

	private final ArrayDeque<Long> presses = new ArrayDeque<>();

	public void press(long nowMs) {
		presses.addLast(nowMs);
		prune(nowMs);
	}

	public int cps(long nowMs) {
		prune(nowMs);
		return presses.size();
	}

	public void clear() {
		presses.clear();
	}

	private void prune(long nowMs) {
		while (!presses.isEmpty() && nowMs - presses.peekFirst() >= WINDOW_MS) {
			presses.removeFirst();
		}
		// A runaway (e.g. a macro mouse) can't grow the queue forever.
		while (presses.size() > 200) presses.removeFirst();
	}
}
