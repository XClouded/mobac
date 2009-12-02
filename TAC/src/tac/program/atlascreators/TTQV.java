package tac.program.atlascreators;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Locale;

import org.openstreetmap.gui.jmapviewer.interfaces.MapSpace;

import tac.exceptions.MapCreationException;
import tac.program.TACInfo;
import tac.program.interfaces.MapInterface;
import tac.tar.TarIndex;
import tac.utilities.Utilities;

public class TTQV extends Ozi {

	@Override
	public void initializeMap(MapInterface map, TarIndex tarTileIndex) {
		super.initializeMap(map, tarTileIndex);
	}

	@Override
	public void createMap() throws MapCreationException {
		try {
			Utilities.mkDir(mapDir);
		} catch (IOException e1) {
			throw new MapCreationException(e1);
		}
		try {
			createTiles();
			writeCalFile();
		} catch (InterruptedException e) {
			// User has aborted process
			return;
		}
	}

	private void writeCalFile() {
		FileOutputStream fout = null;
		try {
			fout = new FileOutputStream(new File(mapDir, mapName + ".cal"));
			OutputStreamWriter mapWriter = new OutputStreamWriter(fout, TEXT_FILE_CHARSET);

			MapSpace mapSpace = mapSource.getMapSpace();

			double longitudeMin = mapSpace.cXToLon(xMin * tileSize, zoom);
			double longitudeMax = mapSpace.cXToLon((xMax + 1) * tileSize, zoom);
			double latitudeMin = mapSpace.cYToLat((yMax + 1) * tileSize, zoom);
			double latitudeMax = mapSpace.cYToLat(yMin * tileSize, zoom);

			int width = (xMax - xMin + 1) * tileSize;
			int height = (yMax - yMin + 1) * tileSize;

			String nsowLine = "%s = 6 = %2.6f\r\n";
			String cLine = "c%d_%s = 7 =  %2.6f\r\n";

			mapWriter.write("; Calibration File for QV Map\r\n");
			mapWriter.write("; generated by " + TACInfo.getCompleteTitle() + "\r\n");
			mapWriter.write("name = 10 = " + mapName + ".png\r\n");
			mapWriter.write("fname = 10 = " + mapName + ".png\r\n");
			mapWriter.write(String.format(Locale.ENGLISH, nsowLine, "nord", latitudeMax));
			mapWriter.write(String.format(Locale.ENGLISH, nsowLine, "sued", latitudeMin));
			mapWriter.write(String.format(Locale.ENGLISH, nsowLine, "ost", longitudeMax));
			mapWriter.write(String.format(Locale.ENGLISH, nsowLine, "west", longitudeMin));
			mapWriter.write("scale_area = 6 =  4.066159e-009\r\n");
			mapWriter.write("proj_mode = 10 = proj\r\n");
			mapWriter.write("projparams = 10 = proj=merc lon_0=-\r\n");
			mapWriter.write("datum1 = 10 = WGS 84# 6378137# 298.257223563# 0# 0# 0#\r\n");
			mapWriter.write("c1_x = 7 =  0\r\n");
			mapWriter.write("c1_y = 7 =  0\r\n");
			mapWriter.write("c2_x = 7 =  " + (width - 1) + "\r\n");
			mapWriter.write("c2_y = 7 =  0\r\n");
			mapWriter.write("c3_x = 7 =  " + (width - 1) + "\r\n");
			mapWriter.write("c3_y = 7 =  " + (height - 1) + "\r\n");
			mapWriter.write("c4_x = 7 =  0\r\n");
			mapWriter.write("c4_y = 7 =  " + (height - 1) + "\r\n");
			mapWriter.write("c5_x = 7 =  0\r\n");
			mapWriter.write("c5_y = 7 =  0\r\n");
			mapWriter.write("c6_x = 7 =  0\r\n");
			mapWriter.write("c6_y = 7 =  0\r\n");
			mapWriter.write("c7_x = 7 =  0\r\n");
			mapWriter.write("c7_y = 7 =  0\r\n");
			mapWriter.write("c8_x = 7 =  0\r\n");
			mapWriter.write("c8_y = 7 =  0\r\n");
			mapWriter.write("c9_x = 7 =  0\r\n");
			mapWriter.write("c9_y = 7 =  0\r\n");
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 1, "lat", latitudeMax));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 1, "lon", longitudeMin));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 2, "lat", latitudeMax));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 2, "lon", longitudeMax));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 3, "lat", latitudeMin));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 3, "lon", longitudeMax));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 4, "lat", latitudeMin));
			mapWriter.write(String.format(Locale.ENGLISH, cLine, 4, "lon", longitudeMin));

			mapWriter.flush();
			mapWriter.close();
		} catch (IOException e) {
			log.error("", e);
		} finally {
			Utilities.closeStream(fout);
		}
	}
}
