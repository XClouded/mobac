package tac.program.download;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import org.apache.log4j.Logger;
import org.openstreetmap.gui.jmapviewer.interfaces.MapSource;
import org.openstreetmap.gui.jmapviewer.interfaces.MapSpace;
import org.openstreetmap.gui.jmapviewer.interfaces.MapSource.TileUpdate;

import tac.exceptions.UnrecoverableDownloadException;
import tac.program.mapcreators.MapDownloadedTileProcessor;
import tac.program.model.Settings;
import tac.tar.TarIndexedArchive;
import tac.tilestore.TileStore;
import tac.tilestore.TileStoreEntry;
import tac.utilities.Utilities;

public class TileDownLoader {

	static {
		System.setProperty("http.maxConnections", "15");
	}

	private static Logger log = Logger.getLogger(TileDownLoader.class);

	private static Settings settings = Settings.getInstance();

	public static int getImage(int layer, int x, int y, int zoom, MapSource mapSource,
			TarIndexedArchive tileArchive) throws IOException, InterruptedException,
			UnrecoverableDownloadException {

		MapSpace mapSpace = mapSource.getMapSpace();
		int maxTileIndex = mapSpace.getMaxPixels(zoom) / mapSpace.getTileSize();
		if (x > maxTileIndex)
			throw new RuntimeException("Invalid tile index x=" + x + " for zoom " + zoom);
		if (y > maxTileIndex)
			throw new RuntimeException("Invalid tile index y=" + y + " for zoom " + zoom);

		TileStore ts = TileStore.getInstance();

		// Thread.sleep(2000);

		// Test code for creating random download failures
		// if (Math.random()>0.7) throw new
		// IOException("intentionally download error");

		Settings s = Settings.getInstance();
		String tileFileName = String.format(MapDownloadedTileProcessor.TILE_FILENAME_PATTERN,
				layer, x, y, mapSource.getTileType());

		TileStoreEntry tile = null;
		if (s.tileStoreEnabled) {

			// Copy the file from the persistent tilestore instead of
			// downloading it from internet.
			tile = ts.getTile(x, y, zoom, mapSource);
			boolean expired = isTileExpired(tile);
			if (tile != null) {
				if (expired) {
					log.trace("Expired: " + mapSource.getName() + " " + tile);
				} else {
					synchronized (tileArchive) {
						log.trace("Tile used from tilestore");
						tileArchive.writeFileFromData(tileFileName, tile.getData());
					}
					return 0;
				}
			}
		}
		byte[] data = null;
		if (tile == null) {
			data = downloadTileAndUpdateStore(x, y, zoom, mapSource);
		} else {
			updateStoredTile(tile, mapSource);
			data = tile.getData();
		}
		if (data == null)
			return 0;
		synchronized (tileArchive) {
			tileArchive.writeFileFromData(tileFileName, data);
		}
		return data.length;
	}

	/**
	 * 
	 * @param x
	 * @param y
	 * @param zoom
	 * @param mapSource
	 * @return
	 * @throws UnrecoverableDownloadException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static byte[] downloadTileAndUpdateStore(int x, int y, int zoom, MapSource mapSource)
			throws UnrecoverableDownloadException, IOException, InterruptedException {
		HttpURLConnection huc = mapSource.getTileUrlConnection(zoom, x, y);
		if (huc == null)
			throw new UnrecoverableDownloadException("Tile x=" + x + " y=" + y + " zoom=" + zoom
					+ " is not a valid tile in map source " + mapSource);

		log.trace("Downloading " + huc.getURL());

		huc.setRequestMethod("GET");

		Settings s = Settings.getInstance();
		huc.setConnectTimeout(1000 * s.connectionTimeout);
		huc.addRequestProperty("User-agent", s.getUserAgent());
		huc.connect();

		int code = huc.getResponseCode();

		if (code != HttpURLConnection.HTTP_OK)
			throw new IOException("Invaild HTTP response: " + code);

		String eTag = huc.getHeaderField("ETag");
		long timeLastModified = huc.getLastModified();
		long timeExpires = huc.getExpiration();

		byte[] data = loadTileInBuffer(huc);
		Utilities.checkForInterruption();
		if (mapSource.allowFileStore() && s.tileStoreEnabled) {
			TileStore.getInstance().putTileData(data, x, y, zoom, mapSource, timeLastModified,
					timeExpires, eTag);
		}
		Utilities.checkForInterruption();
		return data;
	}

	public static byte[] updateStoredTile(TileStoreEntry tile, MapSource mapSource)
			throws UnrecoverableDownloadException, IOException, InterruptedException {
		final int x = tile.getX();
		final int y = tile.getY();
		final int zoom = tile.getZoom();
		final TileUpdate tileUpdate = mapSource.getTileUpdate();

		switch (tileUpdate) {
		case ETag: {
			boolean unchanged = hasTileETag(tile, mapSource);
			if (unchanged) {
				if (log.isTraceEnabled())
					log.trace("Data unchanged on server (eTag): " + mapSource + " " + tile);
				return null;
			}
			break;
		}
		case LastModified: {
			boolean isNewer = isTileNewer(tile, mapSource);
			if (!isNewer) {
				if (log.isTraceEnabled())
					log.trace("Data unchanged on server (LastModified): " + mapSource + " " + tile);
				return null;
			}
			break;
		}
		}
		HttpURLConnection conn = mapSource.getTileUrlConnection(zoom, x, y);
		if (conn == null)
			throw new UnrecoverableDownloadException("Tile x=" + x + " y=" + y + " zoom=" + zoom
					+ " is not a valid tile in map source " + mapSource);

		if (log.isTraceEnabled())
			log.trace(String.format("Checking %s %s", mapSource.getName(), tile));

		conn.setRequestMethod("GET");

		boolean conditionalRequest = false;

		switch (tileUpdate) {
		case IfNoneMatch: {
			if (tile.geteTag() != null) {
				conn.setRequestProperty("If-None-Match", tile.geteTag());
				conditionalRequest = true;
			}
			break;
		}
		case IfModifiedSince: {
			if (tile.getTimeLastModified() > 0) {
				conn.setIfModifiedSince(tile.getTimeLastModified());
				conditionalRequest = true;
			}
			break;
		}
		}

		Settings s = Settings.getInstance();
		conn.setConnectTimeout(1000 * s.connectionTimeout);
		conn.addRequestProperty("User-agent", s.getUserAgent());
		conn.connect();

		int code = conn.getResponseCode();

		if (conditionalRequest && code == HttpURLConnection.HTTP_NOT_MODIFIED) {
			// Data unchanged on server
			if (mapSource.allowFileStore() && s.tileStoreEnabled) {
				tile.update(conn.getExpiration());
				TileStore.getInstance().putTile(tile, mapSource);
			}
			if (log.isTraceEnabled())
				log.trace("Data unchanged on server: " + mapSource + " " + tile);
			return null;
		}

		if (code != HttpURLConnection.HTTP_OK)
			throw new IOException("Invaild HTTP response: " + code);

		String eTag = conn.getHeaderField("ETag");
		long timeLastModified = conn.getLastModified();
		long timeExpires = conn.getExpiration();

		byte[] data = loadTileInBuffer(conn);
		Utilities.checkForInterruption();
		if (mapSource.allowFileStore() && s.tileStoreEnabled) {
			TileStore.getInstance().putTileData(data, x, y, zoom, mapSource, timeLastModified,
					timeExpires, eTag);
		}
		Utilities.checkForInterruption();
		return data;
	}

	public static boolean isTileExpired(TileStoreEntry tileStoreEntry) {
		if (tileStoreEntry == null)
			return true;
		long expiredTime = tileStoreEntry.getTimeExpires();
		if (expiredTime >= 0) {
			// server had set an expiration time
			long maxExpirationTime = settings.tileMaxExpirationTime
					+ tileStoreEntry.getTimeDownloaded();
			long minExpirationTime = settings.tileMinExpirationTime
					+ tileStoreEntry.getTimeDownloaded();
			expiredTime = Math.max(minExpirationTime, Math.min(maxExpirationTime, expiredTime));
		} else {
			// no expiration time set by server - use the default one
			expiredTime = tileStoreEntry.getTimeDownloaded() + settings.tileDefaultExpirationTime;
		}
		return (expiredTime < System.currentTimeMillis());
	}

	protected static byte[] loadTileInBuffer(HttpURLConnection conn) throws IOException {
		InputStream input = conn.getInputStream();
		int bufSize = Math.max(input.available(), 32768);
		ByteArrayOutputStream bout = new ByteArrayOutputStream(bufSize);
		byte[] buffer = new byte[2048];
		boolean finished = false;
		do {
			int read = input.read(buffer);
			if (read >= 0)
				bout.write(buffer, 0, read);
			else
				finished = true;
		} while (!finished);
		if (bout.size() == 0)
			return null;
		return bout.toByteArray();
	}

	/**
	 * Performs a <code>HEAD</code> request for retrieving the
	 * <code>LastModified</code> header value.
	 */
	protected static boolean isTileNewer(TileStoreEntry tile, MapSource mapSource)
			throws IOException {
		long oldLastModified = tile.getTimeLastModified();
		if (oldLastModified <= 0) {
			log.warn("Tile age comparison not possible: "
					+ "tile in tilestore does not contain lastModified attribute");
			return true;
		}
		HttpURLConnection urlConn = mapSource.getTileUrlConnection(tile.getZoom(), tile.getX(),
				tile.getY());
		urlConn.setRequestMethod("HEAD");
		long newLastModified = urlConn.getLastModified();
		if (newLastModified == 0)
			return true;
		return (newLastModified > oldLastModified);
	}

	protected static boolean hasTileETag(TileStoreEntry tile, MapSource mapSource)
			throws IOException {
		String eTag = tile.geteTag();
		if (eTag == null || eTag.length() == 0) {
			log.warn("ETag check not possible: "
					+ "tile in tilestore does not contain ETag attribute");
			return true;
		}
		HttpURLConnection urlConn = mapSource.getTileUrlConnection(tile.getZoom(), tile.getX(),
				tile.getY());
		urlConn.setRequestMethod("HEAD");
		String onlineETag = urlConn.getHeaderField("ETag");
		if (onlineETag == null || onlineETag.length() == 0)
			return true;
		return (onlineETag.equals(eTag));
	}
}