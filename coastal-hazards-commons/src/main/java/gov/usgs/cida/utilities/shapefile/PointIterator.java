package gov.usgs.cida.utilities.shapefile;

import java.util.Iterator;

import org.geotools.geometry.jts.JTSFactoryFinder;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryCollectionIterator;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;

public class PointIterator implements Iterator<Point> {

	private GeometryCollectionIterator gIter;
	private LineString thisGeom = null;
	private int ptCt;
	
	public PointIterator(Geometry shape) {	
		if (shape instanceof LineString) {
			// hack around problem that LineString thinks it contains 1 geometry (besides itself)
			thisGeom = (LineString)shape;
			gIter = null;
		} else if (shape instanceof MultiLineString) {
			gIter = new GeometryCollectionIterator(shape);
		} else {
			throw new ClassCastException("Can't handle this type");
		}
	}
	
	@Override
	public boolean hasNext() {
		if (thisGeom != null) {
			if (ptCt >= thisGeom.getNumPoints()) {
				thisGeom = null;
			}
		}
		
		if (gIter != null) {
			while (thisGeom == null && gIter.hasNext()) {
				Geometry nextGeom = (Geometry)gIter.next();
				// TODO Would be nice to extend this to other collections, but they lack the GetPointN method.
				if ( nextGeom instanceof LineString) {
					thisGeom = (LineString)nextGeom;
					ptCt = 0;
				}
			}
		}
		
		if (thisGeom == null) {
			return false;
		}
		
		return ptCt < thisGeom.getNumPoints();
	}

	@Override
	public Point next() {
		return thisGeom.getPointN(ptCt++);
	}

	@Override
	public void remove() {
	    throw new UnsupportedOperationException(getClass().getName());
	}

}
