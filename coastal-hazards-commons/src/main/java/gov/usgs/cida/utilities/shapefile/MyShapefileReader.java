package gov.usgs.cida.utilities.shapefile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

import org.geotools.data.shapefile.ShpFileType;
import org.geotools.data.shapefile.ShpFiles;
import org.geotools.data.shapefile.dbf.DbaseFileHeader;
import org.geotools.data.shapefile.dbf.DbaseFileReader;
import org.geotools.data.shapefile.shp.ShapeType;
import org.geotools.data.shapefile.shp.ShapefileReader;
import org.geotools.data.shapefile.shp.ShapefileReader.Record;

import com.vividsolutions.jts.geom.CoordinateSequenceFactory;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jtsexample.geom.ExtendedCoordinate;

/** Read a shoreline shapefile, including the M values (which the usual ShpaefileDataStore discards).
 * 
 * @author rhayes
 *
 */
public class MyShapefileReader implements Iterable<ShapeAndAttributes>, Iterator<ShapeAndAttributes> {

	private ShapefileReader rdr;
	private DbaseFileReader dbf;
	public boolean used = false;
	private File file;
	private ShpFiles shpFile;
	
	private final boolean useArcZforMeasure;

	/**
	 * New instance
	 * 
	 * @param shapefileBaseName abs path and base name (w/o extension) of the files within a complete shapefile directory.
	 */
	public MyShapefileReader(String shapefileBaseName) {
		useArcZforMeasure = false;
		
		//Add .shp extension if not present
		if (! shapefileBaseName.toLowerCase().endsWith(".shp")) {
			shapefileBaseName = shapefileBaseName + ".shp";
		}
		
		init(new File(shapefileBaseName), useArcZforMeasure);
	}
	
	/**
	 * New instance
	 * 
	 * @param shapeFile A valid reference to a .shp file within a complete shapefile directory.
	 */
	public MyShapefileReader(File shapeFile) {
		useArcZforMeasure = false;
		
		init(shapeFile, useArcZforMeasure);
	}
	
	/**
	 * If your measure values are encoded in the Z dimention, set useArcZ to true.
	 * 
	 * The typical M measure value tends to be dropped during processing, so
	 * putting the measure value in Z may be safer.
	 * 
	 * @param shapeFile A valid reference to a .shp file within a complete shapefile directory.
	 * @param useArcZ 
	 */
	public MyShapefileReader(File shapeFile, boolean useArcZ) {
		useArcZforMeasure = useArcZ;
		
		init(shapeFile, useArcZforMeasure);
	}
	
	public DbaseFileHeader getDbfHeader() {
		return dbf.getHeader();
	}
	
	public ShpFiles getShpFiles() {
		return shpFile;
	}
	
	/**
	 * 
	 * @param shapeFile A valid reference to a .shp file within a complete shapefile directory.
	 * @param isArcZ 
	 */
	private void init(File shapeFile, boolean isArcZ) {
		file = shapeFile;

		used = false;
		
		try {
			shpFile = new ShpFiles(file);
			CoordinateSequenceFactory x = com.vividsolutions.jtsexample.geom.ExtendedCoordinateSequenceFactory.instance();
			GeometryFactory gf = new GeometryFactory(x);
	
			rdr = new ShapefileReader(shpFile,false, false, gf);
			
			if (isArcZ) {
				rdr.setHandler(new MultiLineZHandler(ShapeType.ARCZ, gf));
			} else {
				rdr.setHandler(new MultiLineZHandler(ShapeType.ARCM, gf));
			}
			
	
			Charset charset = Charset.defaultCharset();		
			dbf = new DbaseFileReader(shpFile, false, charset);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public synchronized Iterator<ShapeAndAttributes> iterator() {
		if (used) {
			init(file, useArcZforMeasure);
		}
		return this;
	}

	@Override
	public synchronized boolean hasNext() {
		try {
			return rdr.hasNext();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public synchronized ShapeAndAttributes next() {
		used = true;
		try {
			Record rec = rdr.nextRecord();
			DbaseFileReader.Row row = dbf.readRow();
			return new ShapeAndAttributes(rec,row);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("Nope, sorry"); 		
	}

	/**
	 * 
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		for (String fn : args) {
			MyShapefileReader ego = new MyShapefileReader(new File(fn));
			for (ShapeAndAttributes saa : ego) {
				System.out.println(saa);
				Geometry geometry = (Geometry) saa.record.shape();
				System.out.println("Geometry: " + geometry);
				MultiLineString mls = (MultiLineString) geometry;
				
				double sumM = 0.0;
				for (Point p : saa) {
					ExtendedCoordinate ec = (ExtendedCoordinate)p.getCoordinate();
					double m = ec.getM();
					sumM += m;
				}
				System.out.printf("sumM: %f\n", sumM);
			}
		}
	}


}
