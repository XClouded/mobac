package tac.mapsources;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.openstreetmap.gui.jmapviewer.interfaces.MapSource;
import org.openstreetmap.gui.jmapviewer.interfaces.MapSpace;

import tac.mapsources.mapspace.MercatorPower2MapSpace;

/**
 * Abstract base class for map sources.
 */
public abstract class AbstractMapSource implements MapSource {

	protected String name;
	protected int minZoom;
	protected int maxZoom;
	protected String tileType;
	protected TileUpdate tileUpdate;

	public AbstractMapSource(String name, int minZoom, int maxZoom, String tileType) {
		this(name, minZoom, maxZoom, tileType, TileUpdate.None);
	}

	public AbstractMapSource(String name, int minZoom, int maxZoom, String tileType,
			TileUpdate tileUpdate) {
		this.name = name;
		this.minZoom = minZoom;
		this.maxZoom = maxZoom;
		this.tileType = tileType;
		this.tileUpdate = tileUpdate;
	}

	
	public HttpURLConnection getTileUrlConnection(int zoom, int tilex, int tiley)
			throws IOException {
		String url = getTileUrl(zoom, tilex, tiley);
		if (url == null)
			return null;
		return (HttpURLConnection) new URL(url).openConnection();
	}

	protected abstract String getTileUrl(int zoom, int tilex, int tiley);

	public int getMaxZoom() {
		return maxZoom;
	}

	public int getMinZoom() {
		return minZoom;
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return name;
	}

	public String getTileType() {
		return tileType;
	}

	public TileUpdate getTileUpdate() {
		return tileUpdate;
	}

	public boolean allowFileStore() {
		return true;
	}

	public MapSpace getMapSpace() {
		return MercatorPower2MapSpace.INSTANCE_256;
	}

}
