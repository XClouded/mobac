package org.openstreetmap.gui.jmapviewer;

//License: GPL. Copyright 2008 by Jan Peter Stotz

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import javax.imageio.ImageIO;

import org.openstreetmap.gui.jmapviewer.interfaces.MapSource;
import org.openstreetmap.gui.jmapviewer.interfaces.TileImageCache;

import tac.utilities.Utilities;

/**
 * Holds one map tile. Additionally the code for loading the tile image and
 * painting it is also included in this class.
 * 
 * @author Jan Peter Stotz
 */
public class Tile {

	/**
	 * Hourglass image that is displayed until a map tile has been loaded
	 */
	public static BufferedImage LOADING_IMAGE;
	public static BufferedImage ERROR_IMAGE;

	static {
		try {
			LOADING_IMAGE = ImageIO.read(Utilities.getResourceImageUrl("hourglass.png"));
			ERROR_IMAGE = ImageIO.read(Utilities.getResourceImageUrl("error.png"));
		} catch (Exception e1) {
			LOADING_IMAGE = null;
			ERROR_IMAGE = null;
		}
	}

	protected MapSource mapSource;
	protected int xtile;
	protected int ytile;
	protected int zoom;
	protected BufferedImage image;
	protected String key;
	protected boolean loaded = false;
	protected boolean loading = false;

	/**
	 * Creates a tile with empty image.
	 * 
	 * @param mapSource
	 * @param xtile
	 * @param ytile
	 * @param zoom
	 */
	public Tile(MapSource mapSource, int xtile, int ytile, int zoom) {
		super();
		this.mapSource = mapSource;
		this.xtile = xtile;
		this.ytile = ytile;
		this.zoom = zoom;
		this.image = LOADING_IMAGE;
		this.key = getTileKey(mapSource, xtile, ytile, zoom);
	}

	public Tile(MapSource source, int xtile, int ytile, int zoom, BufferedImage image) {
		this(source, xtile, ytile, zoom);
		this.image = image;
	}

	/**
	 * Tries to get tiles of a lower or higher zoom level (one or two level
	 * difference) from cache and use it as a placeholder until the tile has
	 * been loaded.
	 */
	public void loadPlaceholderFromCache(TileImageCache cache) {
		int tileSize = mapSource.getMapSpace().getTileSize(); 
		BufferedImage tmpImage = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) tmpImage.getGraphics();
		// g.drawImage(image, 0, 0, null);
		for (int zoomDiff = 1; zoomDiff < 5; zoomDiff++) {
			// first we check if there are already the 2^x tiles
			// of a higher detail level
			int zoom_high = zoom + zoomDiff;
			if (zoomDiff < 3 && zoom_high <= JMapViewer.MAX_ZOOM) {
				int factor = 1 << zoomDiff;
				int xtile_high = xtile << zoomDiff;
				int ytile_high = ytile << zoomDiff;
				double scale = 1.0 / factor;
				g.setTransform(AffineTransform.getScaleInstance(scale, scale));
				int paintedTileCount = 0;
				for (int x = 0; x < factor; x++) {
					for (int y = 0; y < factor; y++) {
						Tile tile = cache
								.getTile(mapSource, xtile_high + x, ytile_high + y, zoom_high);
						if (tile != null && tile.isLoaded()) {
							paintedTileCount++;
							tile.paint(g, x * tileSize, y * tileSize);
						}
					}
				}
				if (paintedTileCount == factor * factor) {
					image = tmpImage;
					return;
				}
			}

			int zoom_low = zoom - zoomDiff;
			if (zoom_low >= JMapViewer.MIN_ZOOM) {
				int xtile_low = xtile >> zoomDiff;
				int ytile_low = ytile >> zoomDiff;
				int factor = (1 << zoomDiff);
				double scale = factor;
				AffineTransform at = new AffineTransform();
				int translate_x = (xtile % factor) * tileSize;
				int translate_y = (ytile % factor) * tileSize;
				at.setTransform(scale, 0, 0, scale, -translate_x, -translate_y);
				g.setTransform(at);
				Tile tile = cache.getTile(mapSource, xtile_low, ytile_low, zoom_low);
				if (tile != null && tile.isLoaded()) {
					tile.paint(g, 0, 0);
					image = tmpImage;
					return;
				}
			}
		}
	}

	public MapSource getSource() {
		return mapSource;
	}

	/**
	 * @return tile number on the x axis of this tile
	 */
	public int getXtile() {
		return xtile;
	}

	/**
	 * @return tile number on the y axis of this tile
	 */
	public int getYtile() {
		return ytile;
	}

	/**
	 * @return zoom level of this tile
	 */
	public int getZoom() {
		return zoom;
	}

	public BufferedImage getImage() {
		return image;
	}

	public void setImage(BufferedImage image) {
		this.image = image;
	}

	public void loadImage(InputStream input) throws IOException {
		image = ImageIO.read(input);
	}

	/**
	 * @return key that identifies a tile
	 */
	public String getKey() {
		return key;
	}

	public boolean isErrorTile() {
		return (ERROR_IMAGE.equals(image));
	}

	public boolean isLoaded() {
		return loaded;
	}

	public void setLoaded(boolean loaded) {
		this.loaded = loaded;
	}

	public HttpURLConnection getUrlConnection() throws IOException {
		return mapSource.getTileUrlConnection(zoom, xtile, ytile);
	}

	/**
	 * Paints the tile-image on the {@link Graphics} <code>g</code> at the
	 * position <code>x</code>/<code>y</code>.
	 * 
	 * @param g
	 * @param x
	 *            x-coordinate in <code>g</code>
	 * @param y
	 *            y-coordinate in <code>g</code>
	 */
	public void paint(Graphics g, int x, int y) {
		if (image == null)
			return;
		g.drawImage(image, x, y, Color.WHITE, null);
	}
	
	public void paintTransparent(Graphics g, int x, int y) {
		if (image == null)
			return;
		g.drawImage(image, x, y, null);
	}
	
	@Override
	public String toString() {
		return "Tile " + key;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Tile))
			return false;
		Tile tile = (Tile) obj;
		return (xtile == tile.xtile) && (ytile == tile.ytile) && (zoom == tile.zoom);
	}

	@Override
	public int hashCode() {
		assert false : "hashCode not designed";
		return -1;
	}

	public static String getTileKey(MapSource source, int xtile, int ytile, int zoom) {
		return zoom + "/" + xtile + "/" + ytile + "@" + source.getName();
	}

}