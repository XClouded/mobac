package tac.program.atlascreators;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.openstreetmap.gui.jmapviewer.interfaces.MapSpace;

import tac.exceptions.MapCreationException;
import tac.program.atlascreators.impl.MapTileBuilder;
import tac.program.atlascreators.impl.MapTileWriter;
import tac.program.interfaces.MapInterface;
import tac.utilities.Utilities;
import tac.utilities.tar.TarIndex;

public class CacheWolf extends Ozi {

	@Override
	public void initializeMap(MapInterface map, TarIndex tarTileIndex) {
		super.initializeMap(map, tarTileIndex);
	}

	@Override
	public void createMap() throws MapCreationException, InterruptedException {
		try {
			Utilities.mkDir(mapDir);
		} catch (IOException e1) {
			throw new MapCreationException(e1);
		}
		if (parameters == null) {
			// One image per map
			super.createTiles();
			writeWflFile();
		} else
			// Use automatic tiling as specified in the parameters
			createTiles();
	}

	@Override
	protected void createTiles() throws InterruptedException, MapCreationException {
		MapTileWriter mapTileWriter;
		try {
			mapDir = mapFolder;
			Utilities.mkDirs(mapDir);
			mapTileWriter = new CWFileTileWriter();
			MapTileBuilder mapTileBuilder = new MapTileBuilder(this, mapTileWriter);
			atlasProgress.initMapCreation(mapTileBuilder.getCustomTileCount());
			mapTileBuilder.createTiles();
		} catch (IOException e) {
			throw new MapCreationException(e);
		}
	}

	private void writeWflFile() throws MapCreationException {
		int width = (xMax - xMin + 1) * tileSize;
		int height = (yMax - yMin + 1) * tileSize;
		try {
			writeWflFile(mapName, 0, 0, width, height);
		} catch (IOException e) {
			throw new MapCreationException("Error writing wfl file: " + e.getMessage(), e);
		}
	}

	private void writeWflFile(String filename, int tilex, int tiley, int width, int height)
			throws IOException {
		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(new File(mapDir, filename + ".wfl"));
			OutputStreamWriter mapWriter = new OutputStreamWriter(fout, TEXT_FILE_CHARSET);

			MapSpace mapSpace = mapSource.getMapSpace();

			int xStart = xMin * tileSize;
			int yStart = yMin * tileSize;

			xStart += tilex * parameters.getWidth();
			yStart += tiley * parameters.getHeight();

			double topLeftLon = mapSpace.cXToLon(xStart, zoom);
			double topLeftLat = mapSpace.cYToLat(yStart, zoom);

			double bottomRightLon = mapSpace.cXToLon(xStart + width, zoom);
			double bottomRightLat = mapSpace.cYToLat(yStart + height, zoom);

			double[] affine = { 0, 0, 0, 0 };

			// TrekBuddy Atlas Creator does only output maps with north at top
			// (no rotation). Therefore we should be able to simplify the affine
			// calculation process:
			affine[1] = (bottomRightLon - topLeftLon) / width;
			affine[2] = (bottomRightLat - topLeftLat) / height;

			for (double d : affine)
				mapWriter.write(Double.toString(d) + "\n");

			mapWriter.write(Double.toString(topLeftLat) + "\n");
			mapWriter.write(Double.toString(topLeftLon) + "\n");
			mapWriter.write(Double.toString(bottomRightLat) + "\n");
			mapWriter.write(Double.toString(bottomRightLon) + "\n");

			mapWriter.flush();
			mapWriter.close();
		} finally {
			Utilities.closeStream(fout);
		}
	}

	public class CWFileTileWriter implements MapTileWriter {

		public CWFileTileWriter() throws IOException {
			super();
			log.debug("Writing tiles to set folder: " + mapDir);
		}

		public void writeTile(int tilex, int tiley, String imageFormat, byte[] tileData)
				throws IOException {
			String tileFileName = String.format("%s_%dx%d", mapName, tilex, tiley);
			File f = new File(mapDir, tileFileName + '.' + imageFormat);
			FileOutputStream out = new FileOutputStream(f);
			try {
				out.write(tileData);
			} finally {
				Utilities.closeStream(out);
			}
			writeWflFile(tileFileName, tilex, tiley, parameters.getWidth(), parameters.getHeight());
		}

		public void finalizeMap() {
		}
	}

}
