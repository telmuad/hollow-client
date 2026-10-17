package dev.telmuad.hollowclient.skyblock.data;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Prices and the NEU item repository, kept up to date in the background while SkyBlock Mode is active. Nothing is
 * downloaded while you aren't on SkyBlock (or in the Hub screen). Plain Java.
 */
public final class SkyBlockData {
	private static final long CHECK_EVERY = 60_000;

	private final Http http;
	private final Prices prices = new Prices();
	private final Election election = new Election();
	private final Contests contests = new Contests();
	private final NeuRepo neu;
	private final LongSupplier clock;
	private final AtomicBoolean busy = new AtomicBoolean();
	private long lastCheck;
	private Consumer<Throwable> errors = t -> {
	};

	public SkyBlockData(Path dir, Http.Fetcher fetcher, LongSupplier clock) {
		this.http = new Http(dir.resolve("cache"), fetcher, clock);
		this.neu = new NeuRepo(dir.resolve("neu"), clock);
		this.clock = clock;
	}

	public void setErrorSink(Consumer<Throwable> sink) {
		this.errors = sink == null ? t -> {
		} : sink;
	}

	public Http http() {
		return http;
	}

	public Prices prices() {
		return prices;
	}

	public NeuRepo neu() {
		return neu;
	}

	public Election election() {
		return election;
	}

	public Contests contests() {
		return contests;
	}

	/** At startup: whatever was downloaded before, without touching the network. */
	public void loadCached() {
		prices.loadCached(http);
		election.loadCached(http);
		contests.loadCached(http);
		neu.open();
	}

	/** Called every tick while active; starts a background refresh once a minute (each source has its own TTL). */
	public void tick(boolean active) {
		if (!active || clock.getAsLong() - lastCheck < CHECK_EVERY) return;
		lastCheck = clock.getAsLong();
		refresh(false);
	}

	/** Refreshes in the background; {@code force} ignores the cache. Returns null when a refresh is already running. */
	public CompletableFuture<Void> refresh(boolean force) {
		if (!busy.compareAndSet(false, true)) return null;
		return CompletableFuture.runAsync(() -> {
			try {
				prices.refresh(http, force);
				election.refresh(http, force);
				contests.refresh(http, force);
				neu.updateIfDue(Http.javaFetcher("application/vnd.github.sha"), Http::download, false);
			} catch (RuntimeException e) {
				errors.accept(e);
			} finally {
				busy.set(false);
			}
		});
	}

	public boolean refreshing() {
		return busy.get();
	}
}
