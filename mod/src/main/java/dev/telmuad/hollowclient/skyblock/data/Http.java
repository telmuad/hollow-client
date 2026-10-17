package dev.telmuad.hollowclient.skyblock.data;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/**
 * Downloads with a disk cache. Every source has a name and a time-to-live: while the cached copy is younger than that,
 * no request is made; when a request fails, the old copy is used and marked stale. Requests run on one background
 * thread, one at a time, so Hollow never hammers a server. Plain Java (the fetcher can be swapped in tests).
 */
public final class Http {
	/** Fetches a URL's body; throws on any failure (including non-200 answers). */
	public interface Fetcher {
		byte[] get(String url) throws IOException;
	}

	/** A body (null if there never was one), when it was fetched, and whether it's an old copy because the fetch failed. */
	public record Result(byte[] body, long fetchedAt, boolean stale, String error) {
		public boolean ok() {
			return body != null;
		}
	}

	/** For the setup checker and feature health. */
	public record Status(long fetchedAt, boolean stale, String error) {
	}

	public static final String USER_AGENT = "HollowClient/2 (+https://github.com/telmuad/hollow-client)";

	private final Path cacheDir;
	private final Fetcher fetcher;
	private final LongSupplier clock;
	private final ExecutorService pool;
	private final Map<String, Status> status = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<Result>> inFlight = new ConcurrentHashMap<>();

	public Http(Path cacheDir, Fetcher fetcher, LongSupplier clock) {
		this.cacheDir = cacheDir;
		this.fetcher = fetcher;
		this.clock = clock;
		this.pool = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "Hollow SkyBlock data");
			t.setDaemon(true);
			return t;
		});
	}

	private static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10)).build();

	/** The real fetcher: Java's HTTP client, 20 s timeout, redirects followed. */
	public static Fetcher javaFetcher() {
		return javaFetcher("application/json");
	}

	public static Fetcher javaFetcher(String accept) {
		HttpClient client = CLIENT;
		return url -> {
			HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
					.header("User-Agent", USER_AGENT).header("Accept", accept).GET().build();
			try {
				HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
				if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
				return res.body();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("interrupted");
			}
		};
	}

	/** Downloads a (big) file straight to disk: 2 minutes, then it gives up. */
	public static void download(String url, Path target) throws IOException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2))
				.header("User-Agent", USER_AGENT).GET().build();
		try {
			Files.createDirectories(target.getParent());
			HttpResponse<Path> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(target));
			if (res.statusCode() != 200) {
				Files.deleteIfExists(target);
				throw new IOException("HTTP " + res.statusCode());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted");
		}
	}

	public Path cacheFile(String name) {
		return cacheDir.resolve(name + ".cache");
	}

	public Status status(String name) {
		return status.get(name);
	}

	/** The cached body, whatever its age (null when there is none). */
	public Result cached(String name) {
		Path f = cacheFile(name);
		try {
			if (!Files.isRegularFile(f)) return new Result(null, 0, true, "not downloaded yet");
			return new Result(Files.readAllBytes(f), Files.getLastModifiedTime(f).toMillis(), false, "");
		} catch (IOException e) {
			return new Result(null, 0, true, e.getMessage());
		}
	}

	/** Fresh cache, or a download (in the background), or the old copy marked stale. Same-name requests share one fetch. */
	public CompletableFuture<Result> get(String name, String url, long ttlMs, boolean force) {
		Result c = cached(name);
		if (!force && c.ok() && clock.getAsLong() - c.fetchedAt() < ttlMs) {
			status.putIfAbsent(name, new Status(c.fetchedAt(), false, ""));
			return CompletableFuture.completedFuture(c);
		}
		return inFlight.computeIfAbsent(name, n -> CompletableFuture.supplyAsync(() -> fetch(name, url), pool)
				.whenComplete((r, t) -> inFlight.remove(name)));
	}

	/** Same as {@link #get} but on the calling thread (tests, and work that's already in the background). */
	public Result getNow(String name, String url, long ttlMs, boolean force) {
		Result c = cached(name);
		if (!force && c.ok() && clock.getAsLong() - c.fetchedAt() < ttlMs) return c;
		return fetch(name, url);
	}

	private Result fetch(String name, String url) {
		try {
			byte[] body = fetcher.get(url);
			long now = clock.getAsLong();
			write(name, body, now);
			status.put(name, new Status(now, false, ""));
			return new Result(body, now, false, "");
		} catch (IOException | RuntimeException e) {
			String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			Result old = cached(name);
			status.put(name, new Status(old.fetchedAt(), true, error));
			return new Result(old.body(), old.fetchedAt(), true, error);
		}
	}

	private void write(String name, byte[] body, long now) throws IOException {
		Files.createDirectories(cacheDir);
		Path f = cacheFile(name);
		Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
		Files.write(tmp, body);
		try {
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(now));
	}

	public void shutdown() {
		pool.shutdownNow();
	}
}
