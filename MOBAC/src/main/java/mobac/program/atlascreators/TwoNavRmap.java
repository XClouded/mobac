/*******************************************************************************
 * Copyright (c) Luka Logar, MOBAC developers
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

/*
 * add to mobac.program.model.AtlasOutputFormat.java
 *
 * import mobac.program.atlascreators.TwoNavRmap;
 * TwoNavRMAP("TwoNav (RMAP)", TwoNavRmap.class), //
 *
 */
package mobac.program.atlascreators;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

import javax.imageio.ImageIO;

import mobac.exceptions.AtlasTestException;
import mobac.exceptions.MapCreationException;
import mobac.mapsources.mapspace.MercatorPower2MapSpace;
import mobac.program.atlascreators.tileprovider.ConvertedRawTileProvider;
import mobac.program.interfaces.AtlasInterface;
import mobac.program.interfaces.LayerInterface;
import mobac.program.interfaces.MapInterface;
import mobac.program.interfaces.MapSource;
import mobac.program.interfaces.MapSpace;
import mobac.program.interfaces.TileImageDataWriter;
import mobac.program.interfaces.MapSpace.ProjectionCategory;
import mobac.program.model.TileImageFormat;
import mobac.program.tiledatawriter.TileImageJpegDataWriter;
import mobac.utilities.Utilities;

import org.apache.log4j.Level;

public class TwoNavRmap extends AtlasCreator {

	private RmapFile rmapFile = null;

	private class ZoomLevel {

		private int index = 0;
		private long offset = 0;
		private int width = 0;
		private int height = 0;
		private int xTiles = 0;
		private int yTiles = 0;
		private long jpegOffsets[][] = null;
		private MapInterface map = null;

		private void writeHeader() throws IOException {
			if (offset == 0) {
				offset = rmapFile.getFilePointer();
			} else {
				rmapFile.seek(offset);
			}
			log.trace(String.format("Writing ZoomLevel %d (%dx%d pixels, %dx%d tiles) header at offset %d", index,
					width, height, xTiles, yTiles, offset));
			rmapFile.writeIntI(width);
			rmapFile.writeIntI(-height);
			rmapFile.writeIntI(xTiles);
			rmapFile.writeIntI(yTiles);
			if (jpegOffsets == null) {
				jpegOffsets = new long[xTiles][yTiles];
			}
			for (int y = 0; y < yTiles; y++) {
				for (int x = 0; x < xTiles; x++) {
					rmapFile.writeLongI(jpegOffsets[x][y]);
				}
			}
		}

		private BufferedImage loadJpegAtOffset(long offset) throws IOException {
			if (offset == 0) {
				throw new IOException("offset == 0");
			}
			rmapFile.seek(offset);
			int TagId = rmapFile.readIntI();
			if (TagId != 7) {
				throw new IOException("TagId != 7");
			}
			int TagLen = rmapFile.readIntI();
			byte[] jpegImageBuf = new byte[TagLen];
			rmapFile.read(jpegImageBuf);
			ByteArrayInputStream input = new ByteArrayInputStream(jpegImageBuf);
			return ImageIO.read(input);
		}

		private byte[] getTileData(ZoomLevel source, int x, int y) throws IOException {
			log.trace(String.format("Shrinking jpegs (%d,%d,%d - %d,%d,%d)", source.index, x, y, source.index, x + 1,
					y + 1));
			BufferedImage bi11 = loadJpegAtOffset(source.jpegOffsets[x][y]);
			BufferedImage bi21 = (x + 1 < source.xTiles) ? loadJpegAtOffset(source.jpegOffsets[x + 1][y]) : null;
			BufferedImage bi12 = (y + 1 < source.yTiles) ? loadJpegAtOffset(source.jpegOffsets[x][y + 1]) : null;
			BufferedImage bi22 = (x + 1 < source.xTiles) && (y + 1 < source.yTiles) ? loadJpegAtOffset(source.jpegOffsets[x + 1][y + 1])
					: null;
			BufferedImage bi = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_3BYTE_BGR);
			Graphics2D g = bi.createGraphics();
			g.drawImage(bi11, 0, 0, null);
			if (bi21 != null) {
				g.drawImage(bi21, bi11.getWidth(), 0, null);
			}
			if (bi12 != null) {
				g.drawImage(bi12, 0, bi11.getHeight(), null);
			}
			if (bi22 != null) {
				g.drawImage(bi22, bi11.getWidth(), bi11.getHeight(), null);
			}
			AffineTransformOp op = new AffineTransformOp(new AffineTransform(0.5, 0, 0, 0.5, 0, 0),
					AffineTransformOp.TYPE_BILINEAR);
			BufferedImage biOut = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
			op.filter(bi, biOut);
			ByteArrayOutputStream buffer = new ByteArrayOutputStream(width * height * 4);
			TileImageDataWriter writer = new TileImageJpegDataWriter(0.9);
			writer.initialize();
			writer.processImage(biOut, buffer);
			return buffer.toByteArray();
		}

		private void shrinkFrom(ZoomLevel source) {
			try {
				writeHeader();
				atlasProgress.initMapCreation(xTiles * yTiles);
				for (int x = 0; x < xTiles; x++) {
					for (int y = 0; y < yTiles; y++) {
						checkUserAbort();
						atlasProgress.incMapCreationProgress();
						jpegOffsets[x][y] = rmapFile.getFilePointer();
						byte[] tileData = getTileData(source, 2 * x, 2 * y);
						rmapFile.seek(jpegOffsets[x][y]);
						log.trace(String.format("Writing shrunk jpeg (%d,%d,%d) at offset %d", index, x, y,
								jpegOffsets[x][y]));
						rmapFile.writeIntI(7);
						rmapFile.writeIntI(tileData.length);
						rmapFile.write(tileData);
					}
				}
			} catch (Exception e) {
				log.error("Failed generating ZoomLevel " + index + ": " + e.getMessage());
			}
		}
	}

	private class RmapFile extends RandomAccessFile {

		private String name = "";
		private int width;
		private int height;
		private int tileWidth = 0;
		private int tileHeight = 0;
		private double longitudeMin = 0;
		private double longitudeMax = 0;
		private double latitudeMin = 0;
		private double latitudeMax = 0;
		private long impOffset = 0;
		private ZoomLevel zoomLevels[] = null;

		private RmapFile(String name) throws FileNotFoundException {
			super(name, "rw");
			this.name = name;
		}

		private int readIntI() throws IOException {
			int ch1 = this.read();
			int ch2 = this.read();
			int ch3 = this.read();
			int ch4 = this.read();
			if ((ch1 | ch2 | ch3 | ch4) < 0) {
				throw new IOException();
			}
			return ((ch4 << 24) + (ch3 << 16) + (ch2 << 8) + (ch1 << 0));
		}

		private void writeIntI(int i) throws IOException {
			write((i >>> 0) & 0xFF);
			write((i >>> 8) & 0xFF);
			write((i >>> 16) & 0xFF);
			write((i >>> 24) & 0xFF);
		}

		private void writeLongI(long l) throws IOException {
			write((int) (l >>> 0) & 0xFF);
			write((int) (l >>> 8) & 0xFF);
			write((int) (l >>> 16) & 0xFF);
			write((int) (l >>> 24) & 0xFF);
			write((int) (l >>> 32) & 0xFF);
			write((int) (l >>> 40) & 0xFF);
			write((int) (l >>> 48) & 0xFF);
			write((int) (l >>> 56) & 0xFF);
		}

		private void writeHeader() throws IOException {
			log.trace("Writing rmap header");
			if (zoomLevels == null) {
				throw new IOException("zoomLevels == null");
			}
			seek(0);
			write("CompeGPSRasterImage".getBytes());
			writeIntI(10);
			writeIntI(7);
			writeIntI(0);
			writeIntI(width);
			writeIntI(-height);
			writeIntI(24);
			writeIntI(1);
			writeIntI(tileWidth);
			writeIntI(tileHeight);
			writeLongI(impOffset);
			writeIntI(0);
			writeIntI(zoomLevels.length);
			for (int n = 0; n < zoomLevels.length; n++) {
				writeLongI(zoomLevels[n].offset);
			}
		}

		private void writeMapInfo() throws IOException {
			if (impOffset == 0) {
				impOffset = getFilePointer();
			} else {
				seek(impOffset);
			}
			log.trace("Writing MAP data at offset %d" + impOffset);
			StringBuffer sbMap = new StringBuffer();
			sbMap.append("CompeGPS MAP File\r\n");
			sbMap.append("<Header>\r\n");
			sbMap.append("Version=2\r\n");
			sbMap.append("VerCompeGPS=MOBAC\r\n");
			sbMap.append("Projection=2,Mercator,\r\n");
			sbMap.append("Coordinates=1\r\n");
			sbMap.append("Datum=WGS 84\r\n");
			sbMap.append("</Header>\r\n");
			sbMap.append("<Map>\r\n");
			sbMap.append("Bitmap=" + new File(name).getName() + "\r\n");
			sbMap.append("BitsPerPixel=0\r\n");
			sbMap.append(String.format("BitmapWidth=%d\r\n", width));
			sbMap.append(String.format("BitmapHeight=%d\r\n", height));
			sbMap.append("Type=10\r\n");
			sbMap.append("</Map>\r\n");
			sbMap.append("<Calibration>\r\n");
			String pointLine = "P%d=%d,%d,A,%s,%s\r\n";
			DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance(Locale.US);
			df.applyPattern("#0.00000000");
			sbMap.append(String.format(pointLine, 0, 0, 0, df.format(longitudeMin), df.format(latitudeMax)));
			sbMap.append(String.format(pointLine, 1, width - 1, 0, df.format(longitudeMax), df.format(latitudeMax)));
			sbMap.append(String.format(pointLine, 2, width - 1, height - 1, df.format(longitudeMax), df
					.format(latitudeMin)));
			sbMap.append(String.format(pointLine, 3, 0, height - 1, df.format(longitudeMin), df.format(latitudeMin)));
			sbMap.append("</Calibration>\r\n");
			sbMap.append("<MainPolygonBitmap>\r\n");
			String polyLine = "M%d=%d,%d\r\n";
			sbMap.append(String.format(polyLine, 0, 0, 0));
			sbMap.append(String.format(polyLine, 1, width, 0));
			sbMap.append(String.format(polyLine, 2, width, height));
			sbMap.append(String.format(polyLine, 3, 0, height));
			sbMap.append("</MainPolygonBitmap>\r\n");
			writeIntI(1);
			writeIntI(sbMap.length());
			write(sbMap.toString().getBytes());
		}
	}

	// ************************************************************

	public TwoNavRmap() {
		super();
		log.setLevel(Level.TRACE);
	}

	@Override
	protected void testAtlas() throws AtlasTestException {
		if (atlas.getLayerCount() != 1) {
			throw new AtlasTestException("Only 1 layer, please");
		}
		for (LayerInterface layer : atlas) {
			for (MapInterface map : layer) {
				if (map.getParameters() == null)
					continue;
				TileImageFormat format = map.getParameters().getFormat();
				if (!(format.getDataWriter() instanceof TileImageJpegDataWriter))
					throw new AtlasTestException("Only JPEG tile format is supported by this atlas format!", map);
			}
		}
	}

	public boolean testMapSource(MapSource mapSource) {
		MapSpace mapSpace = mapSource.getMapSpace();
		return (mapSpace instanceof MercatorPower2MapSpace && ProjectionCategory.SPHERE.equals(mapSpace
				.getProjectionCategory()));
	}

	@Override
	public void startAtlasCreation(AtlasInterface atlas, File customAtlasDir) throws IOException, InterruptedException,
			AtlasTestException {

		super.startAtlasCreation(atlas, customAtlasDir);

		// Logging.configureConsoleLogging(org.apache.log4j.Level.ALL, new SimpleLayout());

		LayerInterface layer = atlas.getLayer(0);

		rmapFile = new RmapFile(atlasDir + "\\" + layer.getName() + ".rmap");

		int DefaultMap = 0;

		rmapFile.width = 0;
		rmapFile.height = 0;
		for (int n = 0; n < layer.getMapCount(); n++) {
			int width = layer.getMap(n).getMaxTileCoordinate().x - layer.getMap(n).getMinTileCoordinate().x + 1;
			int height = layer.getMap(n).getMaxTileCoordinate().y - layer.getMap(n).getMinTileCoordinate().y + 1;
			if ((width > rmapFile.width) || (height > rmapFile.height)) {
				rmapFile.width = width;
				rmapFile.height = height;
				DefaultMap = n;
			}
		}

		log.trace("rmap width  = " + rmapFile.width);
		log.trace("rmap height = " + rmapFile.height);

		rmapFile.tileWidth = layer.getMap(DefaultMap).getTileSize().width;
		rmapFile.tileHeight = layer.getMap(DefaultMap).getTileSize().height;

		log.trace("rmap tileWidth  = " + rmapFile.tileWidth);
		log.trace("rmap tileHeight = " + rmapFile.tileHeight);

		MapSpace mapSpace = layer.getMap(DefaultMap).getMapSource().getMapSpace();
		rmapFile.longitudeMin = mapSpace.cXToLon(layer.getMap(DefaultMap).getMinTileCoordinate().x, layer.getMap(
				DefaultMap).getZoom());
		rmapFile.longitudeMax = mapSpace.cXToLon(layer.getMap(DefaultMap).getMaxTileCoordinate().x, layer.getMap(
				DefaultMap).getZoom());
		rmapFile.latitudeMin = mapSpace.cYToLat(layer.getMap(DefaultMap).getMaxTileCoordinate().y, layer.getMap(
				DefaultMap).getZoom());
		rmapFile.latitudeMax = mapSpace.cYToLat(layer.getMap(DefaultMap).getMinTileCoordinate().y, layer.getMap(
				DefaultMap).getZoom());

		log.trace("rmap longitudeMin = " + rmapFile.longitudeMin);
		log.trace("rmap longitudeMax = " + rmapFile.longitudeMax);
		log.trace("rmap latitudeMin = " + rmapFile.latitudeMin);
		log.trace("rmap latitudeMax = " + rmapFile.latitudeMax);

		double width = rmapFile.width;
		double height = rmapFile.height;
		int count = 0;
		while ((width >= 128.0) || (height >= 128.0)) {
			width = Math.ceil(width / 2.0);
			height = Math.ceil(height / 2.0);
			count++;
		}

		log.trace("rmap zoomLevels count = " + count);

		rmapFile.zoomLevels = new ZoomLevel[count];
		width = rmapFile.width;
		height = rmapFile.height;
		for (int n = 0; n < rmapFile.zoomLevels.length; n++) {
			rmapFile.zoomLevels[n] = new ZoomLevel();
			rmapFile.zoomLevels[n].index = n;
			rmapFile.zoomLevels[n].width = (int) Math.round(width);
			rmapFile.zoomLevels[n].height = (int) Math.round(height);
			rmapFile.zoomLevels[n].xTiles = (int) Math.ceil((double) rmapFile.zoomLevels[n].width
					/ (double) rmapFile.tileWidth);
			rmapFile.zoomLevels[n].yTiles = (int) Math.ceil((double) rmapFile.zoomLevels[n].height
					/ (double) rmapFile.tileHeight);
			rmapFile.zoomLevels[n].jpegOffsets = new long[rmapFile.zoomLevels[n].xTiles][rmapFile.zoomLevels[n].yTiles];
			width = Math.ceil(width / 2.0);
			height = Math.ceil(height / 2.0);
		}

		for (int n = 0; n < layer.getMapCount(); n++) {
			for (int m = 0; m < rmapFile.zoomLevels.length; m++) {
				if ((rmapFile.zoomLevels[m].width == layer.getMap(n).getMaxTileCoordinate().x
						- layer.getMap(n).getMinTileCoordinate().x + 1)
						&& (rmapFile.zoomLevels[m].height == layer.getMap(n).getMaxTileCoordinate().y
								- layer.getMap(n).getMinTileCoordinate().y + 1)) {
					if (rmapFile.zoomLevels[m].map != null) {
						throw new InterruptedException("Only 1 map per ZoomLevel please");
					}
					rmapFile.zoomLevels[m].map = layer.getMap(n);
				}
			}
		}

		for (int n = 0; n < rmapFile.zoomLevels.length; n++) {
			log.trace(String.format("zoomLevels[%d] %dx%d pixels, %dx%d tiles %s", rmapFile.zoomLevels[n].index,
					rmapFile.zoomLevels[n].width, rmapFile.zoomLevels[n].height, rmapFile.zoomLevels[n].xTiles,
					rmapFile.zoomLevels[n].yTiles, rmapFile.zoomLevels[n].map == null ? "calc" : "dl"));
		}

		rmapFile.writeHeader();
	}

	public void createMap() throws MapCreationException, InterruptedException {
		try {

			int index;
			for (index = 0; index < rmapFile.zoomLevels.length; index++) {
				if (rmapFile.zoomLevels[index].map == map) {
					break;
				}
			}
			if (index == rmapFile.zoomLevels.length) {
				throw new MapCreationException("zoomLevel not found");
			}
			try {
				rmapFile.zoomLevels[index].writeHeader();
			} catch (IOException e) {
				throw new MapCreationException("rmapFile.zoomLevels[Index].writeHeader() failed: " + e.getMessage());
			}

			int tilex = 0;
			int tiley = 0;

			atlasProgress.initMapCreation((xMax - xMin + 1) * (yMax - yMin + 1));

			if (!"png".equals(map.getMapSource().getTileType()) || map.getParameters() != null) {
				// Tiles have to be converted to jpeg format
				TileImageFormat imageFormat = TileImageFormat.JPEG90;
				if (map.getParameters() != null)
					imageFormat = map.getParameters().getFormat();
				mapDlTileProvider = new ConvertedRawTileProvider(mapDlTileProvider, imageFormat);
			}

			ImageIO.setUseCache(false);
			byte[] emptyTileData = Utilities.createEmptyTileData(mapSource);
			for (int x = xMin; x <= xMax; x++) {
				tiley = 0;
				for (int y = yMin; y <= yMax; y++) {
					checkUserAbort();
					atlasProgress.incMapCreationProgress();
					try {
						// Remember offset to tile
						rmapFile.zoomLevels[index].jpegOffsets[tilex][tiley] = rmapFile.getFilePointer();
						byte[] sourceTileData = mapDlTileProvider.getTileData(x, y);
						if (sourceTileData != null) {
							rmapFile.writeIntI(7);
							rmapFile.writeIntI(sourceTileData.length);
							rmapFile.write(sourceTileData);
						} else {
							log.trace(String.format("Tile x=%d y=%d not found in tile archive - creating default",
									tilex, tiley));
							rmapFile.writeIntI(7);
							rmapFile.writeIntI(emptyTileData.length);
							rmapFile.write(emptyTileData);
						}
					} catch (IOException e) {
						throw new MapCreationException("Error writing tile image: " + e.getMessage(), e);
					}
					tiley++;
				}
				tilex++;
			}

		} catch (MapCreationException e) {
			throw e;
		} catch (Exception e) {
			throw new MapCreationException(e);
		}
	}

	@Override
	public void abortAtlasCreation() throws IOException {
		try {
			rmapFile.setLength(0);
		} finally {
			Utilities.closeFile(rmapFile);
		}
		super.abortAtlasCreation();
	}

	@Override
	public void finishAtlasCreation() {
		try {
			for (int n = 0; n < rmapFile.zoomLevels.length; n++) {
				if (rmapFile.zoomLevels[n].offset == 0) {
					if (n == 0) {
						throw new Exception("Missing top level map");
					}
					rmapFile.zoomLevels[n].shrinkFrom(rmapFile.zoomLevels[n - 1]);
				}
			}
			rmapFile.writeMapInfo();
			rmapFile.writeHeader();
			for (int n = 0; n < rmapFile.zoomLevels.length; n++) {
				rmapFile.zoomLevels[n].writeHeader();
			}
			rmapFile.close();
		} catch (Exception e) {
			log.error("Failed writing rmap file \"" + rmapFile.name + "\": " + e.getMessage());
		}
		rmapFile = null;
	}
}