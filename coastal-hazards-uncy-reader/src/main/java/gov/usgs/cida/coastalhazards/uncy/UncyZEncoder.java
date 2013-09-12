package gov.usgs.cida.coastalhazards.uncy;

import com.vividsolutions.jts.geom.Coordinate;
import gov.usgs.cida.utilities.shapefile.*;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.shapefile.ShpFileType;
import org.geotools.data.shapefile.ShpFiles;
import org.geotools.data.shapefile.dbf.DbaseFileHeader;
import org.geotools.data.shapefile.dbf.DbaseFileReader;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jtsexample.geom.ExtendedCoordinate;
import java.util.ArrayList;


/** Write a copy of the input shapefile, with lines exploded to their constituent points
 * and with the M columns used to look up the point-by-point uncertainty (if available).
 * 
 * @author rhayes
 *
 */
public class UncyZEncoder {

	public static final String ENCODED_Z_SUFFIX = "_zencode";

	private static Logger logger = LoggerFactory.getLogger(UncyZEncoder.class);
	
	private int geomIdx = -1;
	private FeatureWriter<SimpleFeatureType, SimpleFeature> featureWriter;
	private Map<UncyZEncoder.UncyKey,Double>  uncyMap;
	private int dfltUncyIdx = -1;
	private DbaseFileHeader dbfHdr;
	private Transaction tx;
    private GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

	private int surveyIDIdx;

	private static int locateField(DbaseFileHeader hdr, String nm, Class<?> expected) {
		int idx = -1;
		
		for (int x = 0; x < hdr.getNumFields(); x++) {
			String fnm = hdr.getFieldName(x);
			if (nm.equalsIgnoreCase(fnm)) {
				idx = x;
			}
		}
		if (idx < 0) {
			throw new RuntimeException("did not find column named " + nm);
		}
		
		Class<?> idClass = hdr.getFieldClass(idx);		
		if ( ! expected.isAssignableFrom(idClass)) {
			throw new RuntimeException("Actual class " + idClass + " is not assignable to expected " + expected);
		}

		return idx;
	}
	
	/** Key to point-by-point uncertainty. Must be hashable and ordered (used as lookup key).
	 * 
	 * Uncertainty is joined to points using a two part composit key:
	 * <ol>
	 * <li><b>surveyID</b><br/>:
	 * Named <i>surveyID</i> in both the <i>name<i>.shp and <i>name</i>_uncertainty.dbf files.<br/>
	 * A unique survey identifier (unique w/in a shoreline set) to link all the
	 * MultiLineStrings that make up a survey.  Since a survey may have been done
	 * over multiple days, multiple dates and MultiLineStrings are likely involved.
	 * </li>
	 * <li><b>id<b><br/>:
	 * Named <i>id</i> in the <i>name</i>_uncertainty.dbf and is encoded as the
	 * measure of each coordinate in the MultiLineStrings in the <i>name<i>.shp file
	 * (coord.getM())<br/>
	 * No one on the project seems to know how this ID is generated, but it is
	 * somewhat spatially related and does uniquely identify a point within a
	 * survey.
	 * </li>
	 * 
	 * @author rhayes
	 *
	 */
	public static class UncyKey implements Comparable<UncyZEncoder.UncyKey>{
		private final int idx;
		private final String surveyID;
		
		public UncyKey(int idx, String surveyID) {
			this.idx = idx;
			this.surveyID = surveyID;
		}

		public int getIdx() {
			return idx;
		}

		public String getSurveyID() {
			return surveyID;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + idx;
			result = prime * result
					+ ((surveyID == null) ? 0 : surveyID.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UncyZEncoder.UncyKey other = (UncyZEncoder.UncyKey) obj;
			if (idx != other.idx)
				return false;
			if (surveyID == null) {
				if (other.surveyID != null)
					return false;
			} else if (!surveyID.equals(other.surveyID))
				return false;
			return true;
		}

		@Override
		public int compareTo(UncyZEncoder.UncyKey o) {
			int v;
			
			v = Integer.compare(idx, o.idx);
			if (v != 0) {
				return v;
			}
			if (surveyID == null) {
				if (o.surveyID == null) {
					return 0;
				}
			}
			v = surveyID.compareTo(o.surveyID);
			return v;
		}
		
	}
	
	private static Map<UncyZEncoder.UncyKey,Double> readUncyFromDBF(String fn) throws Exception {
		
		ShpFiles shpFile = new ShpFiles(fn);
		Charset charset = Charset.defaultCharset();
		
		DbaseFileReader rdr = new DbaseFileReader(shpFile, false, charset);
		
		DbaseFileHeader hdr = rdr.getHeader();
		// System.out.println("Header: " + hdr);
		
		int uncyIdx = locateField(hdr, "uncy", Double.class);
		int idIdx = locateField(hdr, "id", Number.class);
		int surveyIdx = locateField(hdr,  "surveyID", String.class);
		
		Map<UncyZEncoder.UncyKey,Double> value = new HashMap<UncyZEncoder.UncyKey,Double>();
		
		while (rdr.hasNext()) {
			Object[] ff = rdr.readEntry();
			
			Integer i = ((Number)ff[idIdx]).intValue();
			Double d = (Double)ff[uncyIdx];
			String surveyID = (String)ff[surveyIdx];
			
			UncyZEncoder.UncyKey key = new UncyZEncoder.UncyKey(i, surveyID);
			value.put(key, d);
		}
		
		rdr.close();
		
		logger.info("Read uncertainty map, size {}", value.size());
		
		return value;
	}
	
	public int processShape(ShapeAndAttributes sap) throws Exception {

		Double defaultUncertainty = (Double)sap.row.read(dfltUncyIdx);
		String surveyID = (String)sap.row.read(surveyIDIdx);
		MultiLineString sourceMultiLine = (MultiLineString) sap.record.shape();
		
		int recordNum = sap.record.number;
		int numGeom = sourceMultiLine.getNumGeometries();
		int ptCt = 0;
		
		LineString[] destLineStr = new LineString[numGeom];
		
		for (int lineStringIdx = 0; lineStringIdx < numGeom; lineStringIdx++) {
			Geometry sourceGeometry = sourceMultiLine.getGeometryN(lineStringIdx);
			
			ArrayList<Coordinate> destCoord = new ArrayList();
			
			PointIterator pIterator = new PointIterator(sourceGeometry);
			while (pIterator.hasNext()) {
				Point p = pIterator.next();
				
				ExtendedCoordinate sourceCoord = (ExtendedCoordinate)p.getCoordinate();
				
				double uncy = defaultUncertainty;
				
				double md = sourceCoord.getM();
				if ( ! Double.isNaN(md)) {
					int mi = (int)md;
					
					UncyZEncoder.UncyKey key = new UncyZEncoder.UncyKey(mi, surveyID);
					Double uv = uncyMap.get(key);
					if (uv != null) {
						uncy = uv;
					}
				}
				
				Coordinate destPoint = new Coordinate(sourceCoord.x, sourceCoord.y, uncy);
				destCoord.add(destPoint);
				
				ptCt ++;
				
			}
			
			Coordinate[] destCoordArray = destCoord.toArray(new Coordinate[destCoord.size()]);
			destLineStr[lineStringIdx] = geometryFactory.createLineString(destCoordArray);
		}
		
		MultiLineString destMultiLine = geometryFactory.createMultiLineString(destLineStr);
		writeMultiLine(destMultiLine, sap.row, recordNum);
		return ptCt;
		
	}
	
	public void writeMultiLine(MultiLineString mls, DbaseFileReader.Row row, int recordNum) throws Exception {
		
		SimpleFeature writeFeature = featureWriter.next();
		
		// geometry field is first, otherwise we lose.
		writeFeature.setAttribute(0, mls);

		// copy them other attributes over, replacing uncy
		int i;
		for (i = 0; i < dbfHdr.getNumFields(); i++) {
			Object value;
			if (i == dfltUncyIdx) {
				value = -1;
			} else {
				value = row.read(i);
			}
			writeFeature.setAttribute(i+1, value);
		}
		// Add record attribute
		writeFeature.setAttribute(i+1, recordNum);
		
		featureWriter.write();
	}
	
	private File initWriter(String fn) throws Exception {
		// read input to get attributes
		SimpleFeatureType sourceSchema = readSourceSchema(fn);
		
		// duplicate input schema, except replace geometry with Point
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		typeBuilder.setName(sourceSchema.getName() + ENCODED_Z_SUFFIX);
		typeBuilder.setCRS(sourceSchema.getCoordinateReferenceSystem());
		
		geomIdx = -1;
		// dfltUncyIdx = -1;
		int idx = 0;
		for (AttributeDescriptor ad : sourceSchema.getAttributeDescriptors()) {
			AttributeType at = ad.getType();
			if (at instanceof GeometryType) {
				typeBuilder.add(ad.getLocalName(), MultiLineString.class);
				geomIdx = idx;
			} else {
				typeBuilder.add(ad.getLocalName(), ad.getType().getBinding());				
			}
			idx++;
		}
		typeBuilder.add("inShape", String.class);
		SimpleFeatureType outputFeatureType = typeBuilder.buildFeatureType();

		logger.debug("Output feature type is {}", outputFeatureType);
		
		File fout = new File(fn + ENCODED_Z_SUFFIX + ".shp");
		
		Map<String, Serializable> connect = new HashMap<String, Serializable> ();
		connect.put("url", fout.toURI().toURL());
        connect.put("create spatial index", Boolean.TRUE);
        
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        ShapefileDataStore outputStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(connect);
        
		outputStore.createSchema(outputFeatureType);

        featureWriter = outputStore.getFeatureWriterAppend(tx);
        
        logger.info("Will write {}", fout.getAbsolutePath());
        
        return fout;
	}

	private static SimpleFeatureType readSourceSchema(String fn)
			throws MalformedURLException, IOException
	{
		File fin = new File(fn+".shp");
		logger.debug("Reading source schema from {}", fin);
		
		Map<String, Serializable> connect = new HashMap<String, Serializable> ();
		connect.put("url", fin.toURI().toURL());

		DataStore inputStore = DataStoreFinder.getDataStore(connect);

		String[] typeNames = inputStore.getTypeNames();
		String typeName = typeNames[0];

		SimpleFeatureSource featureSource = inputStore.getFeatureSource(typeName);
		SimpleFeatureType sourceSchema = featureSource.getSchema();
		
		// this might kill the source schema.
		inputStore.dispose();
		
		logger.debug("Source schema is {}", sourceSchema);
		
		return sourceSchema;
	}
	
	/**
	 * 
	 * @param shapefile File base name (w/o extension) of the files within a complete shapefile directory.
	 * @return
	 * @throws Exception 
	 */
	public File explode(String shapefileBaseName) throws Exception {
		MyShapefileReader rdr = initReader(shapefileBaseName);
		
		logger.debug("Input files from {}\n{}", shapefileBaseName, shapefileNames(rdr.getShpFiles()));
		
		tx = new DefaultTransaction("create");
		File zEncodedFile = initWriter(shapefileBaseName);
		
		// Too bad that the reader classes don't expose the ShpFiles.
		
		int shpCt = 0;
		int ptTotal = 0;
		
		if (geomIdx != 0) {
			throw new RuntimeException("This program only supports input that has the geometry as attribute 0");
		}
		for (ShapeAndAttributes saa : rdr) {
			int ptCt = processShape(saa);
			logger.debug("Wrote {} points for shape {}", ptCt, saa.record.toString());
			
			ptTotal += ptCt;
			shpCt++;
		}
		
		tx.commit();
		
		logger.info("Wrote {} points in {} shapes", ptTotal, shpCt);
		
		return zEncodedFile;
	}

	private static String shapefileNames(ShpFiles shp) {
		StringBuilder sb = new StringBuilder();
		
		Map<ShpFileType, String> m = shp.getFileNames();
		for (Map.Entry<ShpFileType, String> me : m.entrySet()) {
			sb.append(me.getKey()).append("\t").append(me.getValue()).append("\n");
		}
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param shapefileBaseName Abs path and base name (w/o extension) of the files within a complete shapefile directory.
	 * @return
	 * @throws Exception 
	 */
	private MyShapefileReader initReader(String shapefileBaseName) throws Exception {
		MyShapefileReader rdr = new MyShapefileReader(shapefileBaseName);
		
		dbfHdr = rdr.getDbfHeader();
		dfltUncyIdx = locateField(dbfHdr, "uncy", Double.class);
		surveyIDIdx = locateField(dbfHdr, "surveyID", String.class);

		uncyMap = readUncyFromDBF(shapefileBaseName + "_uncertainty.dbf");
		return rdr;
	}
	

	public static void main(String[] args) throws Exception {
		for (String fn : args) {
			UncyZEncoder ego = new UncyZEncoder();

			ego.explode(fn);
			
		}
	}

}
