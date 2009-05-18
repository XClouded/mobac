package org.openstreetmap.gui.jmapviewer;

//License: GPL. Copyright 2008 by Jan Peter Stotz

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.gui.jmapviewer.interfaces.MapMarker;
import org.openstreetmap.gui.jmapviewer.interfaces.MapTileLayer;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.MapSource;

import tac.utilities.Utilities;

/**
 * 
 * Provides a simple panel that displays pre-rendered map tiles loaded from the
 * OpenStreetMap project.
 * 
 * @author Jan Peter Stotz
 * 
 */
public class JMapViewer extends JPanel implements TileLoaderListener {

	private static final long serialVersionUID = 1L;

	/**
	 * Vectors for clock-wise tile painting
	 */
	protected static final Point[] move = { new Point(1, 0), new Point(0, 1), new Point(-1, 0),
			new Point(0, -1) };

	public static final int MAX_ZOOM = 22;
	public static final int MIN_ZOOM = 0;

	protected TileLoader tileLoader;
	protected TileCache tileCache;
	protected MapSource tileSource;

	protected List<MapMarker> mapMarkerList;
	protected boolean mapMarkersVisible;
	protected MapGridLayer mapGridLayer = null;

	protected List<MapTileLayer> mapTileLayers;

	/**
	 * x- and y-position of the center of this map-panel on the world map
	 * denoted in screen pixel regarding the current zoom level.
	 */
	protected Point center;

	/**
	 * Current zoom level
	 */
	protected int zoom;

	protected JSlider zoomSlider;
	protected JButton zoomInButton;
	protected JButton zoomOutButton;

	JobDispatcher jobDispatcher;

	/**
	 * Creates a standard {@link JMapViewer} instance that can be controlled via
	 * mouse: hold right mouse button for moving, double click left mouse button
	 * or use mouse wheel for zooming. Loaded tiles are stored the
	 * {@link MemoryTileCache} and the tile loader uses 4 parallel threads for
	 * retrieving the tiles.
	 */
	public JMapViewer() {
		this(new MemoryTileCache(), 4);
		new DefaultMapController(this);
	}

	public JMapViewer(TileCache tileCache, int downloadThreadCount) {
		super();
		mapTileLayers = new LinkedList<MapTileLayer>();
		tileSource = new OsmTileSource.Mapnik();
		tileLoader = new OsmTileLoader(this);
		this.tileCache = tileCache;
		jobDispatcher = JobDispatcher.getInstance();
		mapMarkerList = new LinkedList<MapMarker>();
		mapMarkersVisible = true;
		setLayout(null);
		initializeZoomSlider();
		setMinimumSize(new Dimension(Tile.SIZE, Tile.SIZE));
		setPreferredSize(new Dimension(400, 400));
		setDisplayPositionByLatLon(50, 9, 3);
		mapTileLayers.add(new DefaultMapTileLayer(this));
		// mapTileLayers.add(new OverlayMapTileLayer(this, new
		// Google.GoogleEarthMapsOverlay()));
	}

	protected void initializeZoomSlider() {
		zoomSlider = new JSlider(MIN_ZOOM, tileSource.getMaxZoom());
		zoomSlider.setOrientation(JSlider.VERTICAL);
		zoomSlider.setBounds(10, 10, 30, 150);
		zoomSlider.setOpaque(false);
		zoomSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				setZoom(zoomSlider.getValue());
			}
		});
		add(zoomSlider);
		int size = 18;
		try {
			ImageIcon icon = Utilities.loadResourceImageIcon("plus.png");
			zoomInButton = new JButton(icon);
		} catch (Exception e) {
			zoomInButton = new JButton("+");
			zoomInButton.setFont(new Font("sansserif", Font.BOLD, 9));
			zoomInButton.setMargin(new Insets(0, 0, 0, 0));
		}
		zoomInButton.setBounds(4, 155, size, size);
		zoomInButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				zoomIn();
			}
		});
		add(zoomInButton);
		try {
			ImageIcon icon = Utilities.loadResourceImageIcon("minus.png");
			zoomOutButton = new JButton(icon);
		} catch (Exception e) {
			zoomOutButton = new JButton("-");
			zoomOutButton.setFont(new Font("sansserif", Font.BOLD, 9));
			zoomOutButton.setMargin(new Insets(0, 0, 0, 0));
		}
		zoomOutButton.setBounds(8 + size, 155, size, size);
		zoomOutButton.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				zoomOut();
			}
		});
		add(zoomOutButton);
	}

	/**
	 * Changes the map pane so that it is centered on the specified coordinate
	 * at the given zoom level.
	 * 
	 * @param lat
	 *            latitude of the specified coordinate
	 * @param lon
	 *            longitude of the specified coordinate
	 * @param zoom
	 *            {@link #MIN_ZOOM} <= zoom level <= {@link #MAX_ZOOM}
	 */
	public void setDisplayPositionByLatLon(double lat, double lon, int zoom) {
		setDisplayPositionByLatLon(new Point(getWidth() / 2, getHeight() / 2), lat, lon, zoom);
	}

	/**
	 * Changes the map pane so that the specified coordinate at the given zoom
	 * level is displayed on the map at the screen coordinate
	 * <code>mapPoint</code>.
	 * 
	 * @param mapPoint
	 *            point on the map denoted in pixels where the coordinate should
	 *            be set
	 * @param lat
	 *            latitude of the specified coordinate
	 * @param lon
	 *            longitude of the specified coordinate
	 * @param zoom
	 *            {@link #MIN_ZOOM} <= zoom level <=
	 *            {@link MapSource#getMaxZoom()}
	 */
	public void setDisplayPositionByLatLon(Point mapPoint, double lat, double lon, int zoom) {
		int x = OsmMercator.LonToX(lon, zoom);
		int y = OsmMercator.LatToY(lat, zoom);
		setDisplayPosition(mapPoint, x, y, zoom);
	}

	public void setDisplayPosition(int x, int y, int zoom) {
		setDisplayPosition(new Point(getWidth() / 2, getHeight() / 2), x, y, zoom);
	}

	public void setDisplayPosition(Point mapPoint, int x, int y, int zoom) {
		if (zoom > tileSource.getMaxZoom() || zoom < MIN_ZOOM)
			return;

		// Get the plain tile number
		Point p = new Point();
		p.x = x - mapPoint.x + getWidth() / 2;
		p.y = y - mapPoint.y + getHeight() / 2;
		center = p;
		setIgnoreRepaint(true);
		try {
			int oldZoom = this.zoom;
			this.zoom = zoom;
			if (oldZoom != zoom)
				zoomChanged(oldZoom);
			if (zoomSlider.getValue() != zoom)
				zoomSlider.setValue(zoom);
		} finally {
			setIgnoreRepaint(false);
			repaint();
		}
	}

	/**
	 * Sets the displayed map pane and zoom level so that all map markers are
	 * visible.
	 */
	public void setDisplayToFitMapMarkers() {
		if (mapMarkerList == null || mapMarkerList.size() == 0)
			return;
		int x_min = Integer.MAX_VALUE;
		int y_min = Integer.MAX_VALUE;
		int x_max = Integer.MIN_VALUE;
		int y_max = Integer.MIN_VALUE;
		int mapZoomMax = tileSource.getMaxZoom();
		for (MapMarker marker : mapMarkerList) {
			int x = OsmMercator.LonToX(marker.getLon(), mapZoomMax);
			int y = OsmMercator.LatToY(marker.getLat(), mapZoomMax);
			x_max = Math.max(x_max, x);
			y_max = Math.max(y_max, y);
			x_min = Math.min(x_min, x);
			y_min = Math.min(y_min, y);
		}
		int height = Math.max(0, getHeight());
		int width = Math.max(0, getWidth());
		// System.out.println(x_min + " < x < " + x_max);
		// System.out.println(y_min + " < y < " + y_max);
		// System.out.println("tiles: " + width + " " + height);
		int newZoom = mapZoomMax;
		int x = x_max - x_min;
		int y = y_max - y_min;
		while (x > width || y > height) {
			// System.out.println("zoom: " + zoom + " -> " + x + " " + y);
			newZoom--;
			x >>= 1;
			y >>= 1;
		}
		x = x_min + (x_max - x_min) / 2;
		y = y_min + (y_max - y_min) / 2;
		int z = 1 << (mapZoomMax - newZoom);
		x /= z;
		y /= z;
		setDisplayPosition(x, y, newZoom);
	}

	public Point2D.Double getPosition() {
		double lon = OsmMercator.XToLon(center.x, zoom);
		double lat = OsmMercator.YToLat(center.y, zoom);
		return new Point2D.Double(lat, lon);
	}

	public Point2D.Double getPosition(Point mapPoint) {
		int x = center.x + mapPoint.x - getWidth() / 2;
		int y = center.y + mapPoint.y - getHeight() / 2;
		double lon = OsmMercator.XToLon(x, zoom);
		double lat = OsmMercator.YToLat(y, zoom);
		return new Point2D.Double(lat, lon);
	}

	/**
	 * Calculates the position on the map of a given coordinate
	 * 
	 * @param lat
	 * @param lon
	 * @return point on the map or <code>null</code> if the point is not visible
	 */
	public Point getMapPosition(double lat, double lon) {
		int x = OsmMercator.LonToX(lon, zoom);
		int y = OsmMercator.LatToY(lat, zoom);
		x -= center.x - getWidth() / 2;
		y -= center.y - getHeight() / 2;
		if (x < 0 || y < 0 || x > getWidth() || y > getHeight())
			return null;
		return new Point(x, y);
	}

	@Override
	protected void paintComponent(Graphics g) {
		// if (mapIsMoving) {
		// mapIsMoving = false;
		// Doesn't look very pretty but is much more faster
		// g.copyArea(0, 0, getWidth(), getHeight(), -mapMoveX, -mapMoveY);
		// return;
		// }
		super.paintComponent(g);

		int iMove = 0;

		int tilex = center.x / Tile.SIZE;
		int tiley = center.y / Tile.SIZE;
		int off_x = (center.x % Tile.SIZE);
		int off_y = (center.y % Tile.SIZE);

		int w2 = getWidth() / 2;
		int h2 = getHeight() / 2;
		int posx = w2 - off_x;
		int posy = h2 - off_y;

		int diff_left = off_x;
		int diff_right = Tile.SIZE - off_x;
		int diff_top = off_y;
		int diff_bottom = Tile.SIZE - off_y;

		boolean start_left = diff_left < diff_right;
		boolean start_top = diff_top < diff_bottom;

		if (start_top) {
			if (start_left)
				iMove = 2;
			else
				iMove = 3;
		} else {
			if (start_left)
				iMove = 1;
			else
				iMove = 0;
		} // calculate the visibility borders
		int x_min = -Tile.SIZE;
		int y_min = -Tile.SIZE;
		int x_max = getWidth();
		int y_max = getHeight();

		// paint the tiles in a spiral, starting from center of the map
		boolean painted = (mapTileLayers.size() > 0);
		int x = 0;
		while (painted) {
			painted = false;
			for (int i = 0; i < 4; i++) {
				if (i % 2 == 0)
					x++;
				for (int j = 0; j < x; j++) {
					if (x_min <= posx && posx <= x_max && y_min <= posy && posy <= y_max) {
						// tile is visible
						painted = true;
						for (MapTileLayer l : mapTileLayers) {
							l.paintTile(g, posx, posy, tilex, tiley, zoom);
						}
					}
					Point p = move[iMove];
					posx += p.x * Tile.SIZE;
					posy += p.y * Tile.SIZE;
					tilex += p.x;
					tiley += p.y;
				}
				iMove = (iMove + 1) % move.length;
			}
		}
		// outer border of the map
		int mapSize = Tile.SIZE << zoom;
		g.drawRect(w2 - center.x, h2 - center.y, mapSize, mapSize);

		// g.drawString("Tiles in cache: " + tileCache.getTileCount(), 50, 20);
		if (!mapMarkersVisible || mapMarkerList == null)
			return;
		for (MapMarker marker : mapMarkerList) {
			Point p = getMapPosition(marker.getLat(), marker.getLon());
			// System.out.println(marker + " -> " + p);
			if (p != null)
				marker.paint(g, p);
		}
	}

	/**
	 * Moves the visible map pane.
	 * 
	 * @param x
	 *            horizontal movement in pixel.
	 * @param y
	 *            vertical movement in pixel
	 */
	public void moveMap(int x, int y) {
		center.x += x;
		center.y += y;
		repaint();
	}

	/**
	 * @return the current zoom level
	 */
	public int getZoom() {
		return zoom;
	}

	/**
	 * Increases the current zoom level by one
	 */
	public void zoomIn() {
		setZoom(zoom + 1);
	}

	/**
	 * Increases the current zoom level by one
	 */
	public void zoomIn(Point mapPoint) {
		setZoom(zoom + 1, mapPoint);
	}

	/**
	 * Decreases the current zoom level by one
	 */
	public void zoomOut() {
		setZoom(zoom - 1);
	}

	/**
	 * Decreases the current zoom level by one
	 */
	public void zoomOut(Point mapPoint) {
		setZoom(zoom - 1, mapPoint);
	}

	public void setZoom(int zoom, Point mapPoint) {
		if (zoom > tileSource.getMaxZoom() || zoom < tileSource.getMinZoom() || zoom == this.zoom)
			return;
		Point2D.Double zoomPos = getPosition(mapPoint);
		jobDispatcher.cancelOutstandingJobs(); // Clearing outstanding load
		// requests
		setDisplayPositionByLatLon(mapPoint, zoomPos.x, zoomPos.y, zoom);
	}

	public void setZoom(int zoom) {
		setZoom(zoom, new Point(getWidth() / 2, getHeight() / 2));
	}

	/**
	 * Every time the zoom level changes this method is called. Override it in
	 * derived implementations for adapting zoom dependent values. The new zoom
	 * level can be obtained via {@link #getZoom()}.
	 * 
	 * @param oldZoom
	 *            the previous zoom level
	 */
	protected void zoomChanged(int oldZoom) {
		zoomSlider.setToolTipText("Zoom level " + zoom);
		zoomInButton.setToolTipText("Zoom to level " + (zoom + 1));
		zoomOutButton.setToolTipText("Zoom to level " + (zoom - 1));
		zoomOutButton.setEnabled(zoom > tileSource.getMinZoom());
		zoomInButton.setEnabled(zoom < tileSource.getMaxZoom());
	}

	public boolean isTileGridVisible() {
		return (mapGridLayer != null);
	}

	public void setTileGridVisible(boolean tileGridVisible) {
		if (isTileGridVisible() == tileGridVisible)
			return;
		if (tileGridVisible) {
			mapGridLayer = new MapGridLayer();
			addMapTileLayers(mapGridLayer);
		} else {
			removeMapTileLayers(mapGridLayer);
			mapGridLayer = null;
		}
		repaint();
	}

	public boolean getMapMarkersVisible() {
		return mapMarkersVisible;
	}

	/**
	 * Enables or disables painting of the {@link MapMarker}
	 * 
	 * @param mapMarkersVisible
	 * @see #addMapMarker(MapMarker)
	 * @see #getMapMarkerList()
	 */
	public void setMapMarkerVisible(boolean mapMarkersVisible) {
		this.mapMarkersVisible = mapMarkersVisible;
		repaint();
	}

	public void setMapMarkerList(List<MapMarker> mapMarkerList) {
		this.mapMarkerList = mapMarkerList;
		repaint();
	}

	public List<MapMarker> getMapMarkerList() {
		return mapMarkerList;
	}

	public void addMapMarker(MapMarker marker) {
		mapMarkerList.add(marker);
	}

	public void setZoomContolsVisible(boolean visible) {
		zoomSlider.setVisible(visible);
		zoomInButton.setVisible(visible);
		zoomOutButton.setVisible(visible);
	}

	public boolean getZoomContolsVisible() {
		return zoomSlider.isVisible();
	}

	public TileCache getTileCache() {
		return tileCache;
	}

	public TileLoader getTileLoader() {
		return tileLoader;
	}

	public void setTileLoader(TileLoader tileLoader) {
		this.tileLoader = tileLoader;
	}

	public MapSource getTileLayerSource() {
		return tileSource;
	}

	public MapSource getTileSource() {
		return tileSource;
	}

	public void setTileSource(MapSource tileSource) {
		if (tileSource.getMaxZoom() > MAX_ZOOM)
			throw new RuntimeException("Maximum zoom level too high");
		if (tileSource.getMinZoom() < MIN_ZOOM)
			throw new RuntimeException("Minumim zoom level too low");
		this.tileSource = tileSource;
		zoomSlider.setMinimum(tileSource.getMinZoom());
		zoomSlider.setMaximum(tileSource.getMaxZoom());
		jobDispatcher.cancelOutstandingJobs();
		if (zoom > tileSource.getMaxZoom())
			setZoom(tileSource.getMaxZoom());
		repaint();
	}

	public void tileLoadingFinished(Tile tile, boolean success) {
		repaint();
	}

	public void addMapTileLayers(MapTileLayer mapTileLayer) {
		mapTileLayers.add(mapTileLayer);
	}

	public void removeMapTileLayers(MapTileLayer mapTileLayer) {
		mapTileLayers.remove(mapTileLayer);
	}

}
