package gov.usgs.cida.utilities.shapefile;

import java.util.Iterator;

import org.geotools.data.shapefile.dbf.DbaseFileReader;
import org.geotools.data.shapefile.dbf.DbaseFileReader.Row;
import org.geotools.data.shapefile.shp.ShapefileReader.Record;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class ShapeAndAttributes 
implements Iterable<Point>
{
	public final Record record;						// shape
	public final DbaseFileReader.Row row;				// dbf record
	
	public ShapeAndAttributes(Record record, Row row) {
		super();
		this.record = record;
		this.row = row;
	}

	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ShapeAndAttributes [");
		builder.append("record=").append(record);
		builder.append(",");
		builder.append("row=").append(row);
		builder.append("]");
		return builder.toString();
	}


	@Override
	public Iterator<Point> iterator() {
		return new PointIterator((Geometry)record.shape());
	}
}