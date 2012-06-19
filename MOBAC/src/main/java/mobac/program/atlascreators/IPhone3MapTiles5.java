/*******************************************************************************
 * Copyright (c) MOBAC developers
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
package mobac.program.atlascreators;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;

import mobac.exceptions.AtlasTestException;
import mobac.mapsources.mapspace.MercatorPower2MapSpace;
import mobac.program.annotations.AtlasCreatorName;
import mobac.program.interfaces.LayerInterface;
import mobac.program.interfaces.MapInterface;
import mobac.program.interfaces.MapSource;
import mobac.program.model.TileImageParameters;
import mobac.program.model.TileImageType;

/**
 * The following SQL statements create SQLite database for background map:
 * 
 * BEGIN TRANSACTION; CREATE TABLE images(zoom int, x int, y int, flags int, length int, data blob); CREATE TABLE
 * version(version int); INSERT INTO version VALUES(5); INSERT INTO version VALUES(0);
 * 
 * CREATE INDEX index1 on images (zoom,x,y,flags); COMMIT;
 * 
 * The columns in "images" table have the following meaning:
 * 
 * zoom - zoom level from 1 (top level) to 23 (most detailed level). On the top level we have four squares 128x128
 * pixels, it gives 512x512 pixels. So map width = map height = 256 * 2zoom pixels.
 * 
 * x, y - coordinates for given zoom level:
 * 
 * 
 * 
 * flags - layer number. Theoretically map could contain number of layers: street, satellite, hybrid and so on.
 * Practically the program shows only one layer with smallest flags value.
 * 
 * length - size of binary image in bytes.
 * 
 * data - binary tile image in PNG or JPEG format. Practically the program supports only PNG for now.
 * 
 * For more details regarding Web Mercator projection and coordinates please see:
 * 
 * http://msdn.microsoft.com/en-us/library/bb259689.aspx
 */
@AtlasCreatorName(value = "iPhone 3 Map Tiles v5")
public class IPhone3MapTiles5 extends RMapsSQLite {

	private static final String INSERT_SQL = "INSERT or REPLACE INTO images(x,y,zoom,data,length,flags) VALUES (?,?,?,?,?,0);";
	private static final String TABLE_IMAGES = "CREATE TABLE IF NOT EXISTS images(zoom int, x int, y int, flags int, length int, data blob); ";
	private static final String TABLE_VERSION = "CREATE TABLE IF NOT EXISTS version(version int);";
	private static final String TABLE_VERSION_DATA = "INSERT INTO version VALUES(0);";
	private static final String INDEX_IMAGES = "CREATE INDEX IF NOT EXISTS index1 on images (zoom,x,y,flags);";

	@Override
	public boolean testMapSource(MapSource mapSource) {
		return MercatorPower2MapSpace.INSTANCE_256.equals(mapSource.getMapSpace());
	}

	@Override
	protected void testAtlas() throws AtlasTestException {
		EnumSet<TileImageType> allowed = EnumSet.of(TileImageType.JPG, TileImageType.PNG);
		// Test of output format - only jpg xor png is allowed
		for (LayerInterface layer : atlas) {
			for (MapInterface map : layer) {
				TileImageParameters parameters = map.getParameters();
				TileImageType currentTit;
				if (parameters == null) {
					currentTit = map.getMapSource().getTileImageType();
					if (!allowed.contains(currentTit))
						throw new AtlasTestException(
								"Map source format incompatible - tile format conversion to PNG or JPG is required for this map.",
								map);
				} else {
					currentTit = parameters.getFormat().getType();
					if (!allowed.contains(currentTit))
						throw new AtlasTestException(
								"Selected custom tile format not supported - only JPG and PNG formats are supported.",
								map);
				}
			}
		}
	}

	@Override
	protected void openConnection() throws SQLException {
		if (databaseFile.isFile()) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
			databaseFile = new File(atlasDir, atlas.getName() + "_" + sdf.format(new Date()) + ".sqlitedb");
		}
		super.openConnection();
	}

	@Override
	protected void initializeDB() throws SQLException {
		Statement stat = conn.createStatement();
		stat.executeUpdate(TABLE_IMAGES);
		stat.executeUpdate(INDEX_IMAGES);
		if (stat.executeUpdate(TABLE_VERSION) == 0)
			stat.execute(TABLE_VERSION_DATA);
		stat.close();
	}

	@Override
	protected void updateTileMetaInfo() throws SQLException {
	}

	@Override
	protected String getTileInsertSQL() {
		return INSERT_SQL;
	}

	@Override
	protected void writeTile(int x, int y, int z, byte[] tileData) throws SQLException, IOException {
		y = (1 << z) - y - 1;
		prepStmt.setInt(1, x);
		prepStmt.setInt(2, y);
		prepStmt.setInt(3, z);
		prepStmt.setBytes(4, tileData);
		prepStmt.setInt(5, tileData.length);
		prepStmt.addBatch();
	}

	protected String getDatabaseFileName() {
		return atlas.getName() + ".sqlitedb";
	}

}