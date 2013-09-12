package gov.usgs.cida.coastalhazards.uncy;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import gov.usgs.cida.utilities.shapefile.MyShapefileReader;
import gov.usgs.cida.utilities.shapefile.PointIterator;
import gov.usgs.cida.utilities.shapefile.ShapeAndAttributes;
import java.io.File;
import java.util.Iterator;
import static org.junit.Assert.*;

import org.junit.Test;

public class UncyZEncoderTest {
	
	private static final double COMP_ERR = .000000001D;

	@Test
	public void testExplode() throws Exception {
		UncyZEncoder ego = new UncyZEncoder();

		File resultFile = ego.explode("src/test/resources/test_data");
		
		assertTrue("survived", true);
		
		MyShapefileReader shpReader = initReader(resultFile);
		
		for (ShapeAndAttributes saa : shpReader) {

			assertTrue(saa.record.shape() instanceof MultiLineString);
			
			MultiLineString multiLine = (MultiLineString) saa.record.shape();
			
			checkMls(multiLine, null);
			
			
		}
	}
	
	@Test
	public void testSomeIndividualCoords() throws Exception {
		UncyZEncoder ego = new UncyZEncoder();

		File resultFile = ego.explode("src/test/resources/test_data");
		
		assertTrue("survived", true);
		
		MyShapefileReader shpReader = initReader(resultFile);
		
		//Check a few values directly from the file
		Iterator<ShapeAndAttributes> saai = shpReader.iterator();
		
		//The first five MultiLineStrings all have individual point uncertainties
		//associated w/ them.  Here we check the first 4 points of the 1st MLS.
		ShapeAndAttributes saa = saai.next();	//0
		MultiLineString mls = (MultiLineString) saa.record.shape();
		LineString ls = (LineString) mls.getGeometryN(0);
		assertEquals(1.77d, ls.getCoordinateN(0).z, COMP_ERR);
		assertEquals(1.58d, ls.getCoordinateN(1).z, COMP_ERR);
		assertEquals(2.31d, ls.getCoordinateN(2).z, COMP_ERR);
		assertEquals(1.61d, ls.getCoordinateN(3).z, COMP_ERR);
		
		saai.next();	//1
		saai.next();	//2
		saai.next();	//3
		saai.next();	//4
		saai.next();	//5	- This MLS does not have point uncy and has shape-wide uncy of 3.2

		mls = (MultiLineString) saa.record.shape();
		checkMls(mls, 3.2D);	//Should all be 3.2
		

	}
	
	/**
	 * Simple check of uncy and geom types.
	 * 
	 * Uncy is compared only if non-null
	 * @param multiLine
	 * @param expectedUncy 
	 */
	public void checkMls(MultiLineString multiLine, Double expectedUncy) {
		assertEquals(1, multiLine.getDimension());

		for (int segment = 0; segment < multiLine.getNumGeometries(); segment++) {
			Geometry g = multiLine.getGeometryN(segment);

			assertTrue(g instanceof LineString);

			PointIterator pIterator = new PointIterator(g);

			while (pIterator.hasNext()) {
				Point p = pIterator.next();
				assertEquals(0, p.getDimension());
				Coordinate c = p.getCoordinate();
				
				if (expectedUncy != null) {
					assertEquals(expectedUncy.doubleValue(), c.z, COMP_ERR);
				} else {
					assertTrue(c.z > 0d);
				}
				
				//System.out.println("uncertainty as z: " + c.z);
			}

		}
	}
	
	private MyShapefileReader initReader(File shapeFile) throws Exception {
		MyShapefileReader rdr = new MyShapefileReader(shapeFile, true);
		return rdr;
	}
	
	


}
