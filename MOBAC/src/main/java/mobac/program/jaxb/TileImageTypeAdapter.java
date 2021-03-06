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
package mobac.program.jaxb;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import mobac.program.model.TileImageType;

public class TileImageTypeAdapter extends XmlAdapter<String, TileImageType> {

	@Override
	public TileImageType unmarshal(String v) throws Exception {
		return TileImageType.getTileImageType(v);
	}

	@Override
	public String marshal(TileImageType v) throws Exception {
		return v.name();
	}

}
