/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.usgs.cida.coastalhazards.wps.geom;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import gov.usgs.cida.coastalhazards.util.AttributeGetter;
import gov.usgs.cida.coastalhazards.util.Constants;
import gov.usgs.cida.coastalhazards.wps.exceptions.UnsupportedValueException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author eeverman
 */
public class IntersectionTest {

	//Allowed error for double comparison
	private static final double COMP_ERROR = .000000001d;
	
	
	private GeometryFactory factory;

	
	
	@Before
	public void setup() {
		factory = new GeometryFactory();
	}
	
	/**
	 * 
	 * @param x1
	 * @param y1
	 * @param uncy1
	 * @param x2
	 * @param y2
	 * @param uncy2
	 * @param completeFeatureUncy uncertainty of the entire MultiLineString feature
	 * @param intersectionPercentage decimal percentage of intersetion from pt 1 to 2.
	 * @return 
	 */
	protected Intersection buildIntersection(Double x1, Double y1, Double uncy1, 
			Double x2, Double y2, Double uncy2,
			Double completeFeatureUncy,
			double intersectionPercentage) {
		
        Coordinate a;
		Coordinate b;
		
		if (uncy1 != null) {
			a = new Coordinate(x1, y1, uncy1);
		} else {
			a = new Coordinate(x1, y1);
		}
		
		if (uncy2 != null) {
			b = new Coordinate(x2, y2, uncy2);
		} else {
			b = new Coordinate(x2, y2);
		}
		LineSegment segmentSeg = new LineSegment(a, b);
        LineString segmentLineStr = segmentSeg.toGeometry(factory);
		
		Coordinate intersectCoord = segmentSeg.pointAlong(intersectionPercentage);
		Point intersectPoint = factory.createPoint(intersectCoord);
		
		MultiLineString shoreMultiLine = factory.createMultiLineString(new LineString[] {segmentLineStr});
		
		SimpleFeatureType type = buildSimpleFeatureType();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
		featureBuilder.add(shoreMultiLine);
		featureBuilder.add(completeFeatureUncy);
		
		SimpleFeature shoreFeature = featureBuilder.buildFeature(null);
		AttributeGetter attrGetter = new AttributeGetter(shoreFeature.getType());
		
		Intersection intersection = new Intersection(intersectPoint, 10d, shoreFeature, segmentLineStr, 99, attrGetter);

		return intersection;
	}
	
	protected SimpleFeatureType buildSimpleFeatureType() {
		SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
		
		typeBuilder.setName("Intersections");
		typeBuilder.setCRS(Constants.REQUIRED_CRS_WGS84);
		typeBuilder.add(Constants.DEFAULT_GEOM_ATTR, MultiLineString.class);
		typeBuilder.add(Constants.UNCY_ATTR, Double.class);
		
		
		return typeBuilder.buildFeatureType();
	}
	
	
	@Test
	public void linearAverageCalcOnDiaganal() {
		//
		//All line segments are on a 3-4-5 right triangle to simplify length calcs
		
		//
		//Super simple case - intersection is exactly in the middle of the segment
		Intersection intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				3d, 4d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		
		assertEquals(1.5d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is exactly at point 1
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				3d, 4d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				0 /* intersection location b/t the two points */); 
		
		assertEquals(1d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is exactly at point 2
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				3d, 4d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				1 /* intersection location b/t the two points */); 
		
		assertEquals(2d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is .8 of the way b/t the points
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				3d, 4d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
	}
	
	@Test
	public void linearAverageCalcWithOtherOrientations(){
		//
		//All line segments lenth 5
		
		//
		//Going Up, .8 along the line
		Intersection intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				0d, 5d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Going right, .8 along the line
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				5d, 0d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
	
		//
		//Going down, .8 along the line
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				0d, -5d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Going down off origen, .8 along the line
		intersect =  buildIntersection(
				-99d, 0d, 1d, /* x, y, uncy    for point 1 */
				-99d, -5d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Going left, .8 along the line
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				-5d, 0d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Going left, off origen, .8 along the line
		intersect =  buildIntersection(
				0d, 99d, 1d, /* x, y, uncy    for point 1 */
				-5d, 99d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
	}
	
	@Test
	public void linearAverageCalcWithAcceptableEdgeCaseValues() {
		//
		//Most line segments are on a 3-4-5 right triangle to simplify length calcs
		
		
		//
		//Intersection is exactly in the middle of the segment, zero uncy values
		Intersection intersect =  buildIntersection(
				0d, 0d, 0d, /* x, y, uncy    for point 1 */
				3d, 4d, 0d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		
		assertEquals(0d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is exactly at point 1, zero uncy values
		intersect =  buildIntersection(
				0d, 0d, 0d, /* x, y, uncy    for point 1 */
				3d, 4d, 0d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				0 /* intersection location b/t the two points */); 
		
		assertEquals(0d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is exactly at point 2, zero uncy values
		intersect =  buildIntersection(
				0d, 0d, 0d, /* x, y, uncy    for point 1 */
				3d, 4d, 0d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				1 /* intersection location b/t the two points */); 
		
		assertEquals(0d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Segment is zero length, zero uncy values
		intersect =  buildIntersection(
				0d, 0d, 0d, /* x, y, uncy    for point 1 */
				0d, 0d, 0d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				1 /* intersection location b/t the two points */); 
		
		assertEquals(0d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Segment is zero length, should take the smaller of the uncy values
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				0d, 0d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				1 /* intersection location b/t the two points */); 
		
		assertEquals(1d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Segment is zero length and off-origen, should take the smaller of the uncy values
		intersect =  buildIntersection(
				123456d, 123456d, 1d, /* x, y, uncy    for point 1 */
				123456d, 123456d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				1 /* intersection location b/t the two points */); 
		
		assertEquals(1d, intersect.getUncertainty(), COMP_ERROR);

	}
	
	
	
	@Test(expected = UnsupportedValueException.class)
	public void linearAverageCalcWithNegativePointUncertainty() {
		//
		//Most line segments are on a 3-4-5 right triangle to simplify length calcs
		
		
		//
		//Intersection is exactly in the middle of the segment, zero uncy values
		Intersection intersect =  buildIntersection(
				0d, 0d, -1d, /* x, y, uncy    for point 1 */
				3d, 4d, 0d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		
		assertEquals(999999d, intersect.getUncertainty(), COMP_ERROR);
		
	}
	
	

	@Test
	public void linearAverageCalcWithNegativeCoords() {
		//
		//Most line segments are on a 3-4-5 right triangle to simplify length calcs
		
		
		//
		//Intersection is exactly in the middle of the segment
		Intersection intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				-3d, -4d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		
		assertEquals(1.5d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is exactly in the middle of the segment
		intersect =  buildIntersection(
				-3d, -4d, 1d, /* x, y, uncy    for point 1 */
				0d, 0d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		
		assertEquals(1.5d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is exactly in the middle of the segment
		intersect =  buildIntersection(
				-2d, -3d, 1d, /* x, y, uncy    for point 1 */
				1d, 1d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		
		assertEquals(1.5d, intersect.getUncertainty(), COMP_ERROR);
		
		//
		//Intersection is .8 from a to b
		intersect =  buildIntersection(
				-2d, -3d, 1d, /* x, y, uncy    for point 1 */
				1d, 1d, 2d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.8 /* intersection location b/t the two points */); 
		
		assertEquals(1.8d, intersect.getUncertainty(), COMP_ERROR);
	}
	
	@Test
	public void useFeatureUncyIfPointUncyNotPresent() {

		
		
		Intersection intersect =  buildIntersection(
				0d, 0d, Double.NaN, /* x, y, uncy    for point 1 */
				3d, 4d, Double.NaN, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		assertEquals(777d, intersect.getUncertainty(), COMP_ERROR);
		
		intersect =  buildIntersection(
				0d, 0d, null, /* x, y, uncy    for point 1 */
				3d, 4d, null, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		assertEquals(777d, intersect.getUncertainty(), COMP_ERROR);
		
		intersect =  buildIntersection(
				0d, 0d, 1d, /* x, y, uncy    for point 1 */
				3d, 4d, Double.NaN, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		assertEquals(777d, intersect.getUncertainty(), COMP_ERROR);
		
		intersect =  buildIntersection(
				0d, 0d, Double.NaN, /* x, y, uncy    for point 1 */
				3d, 4d, 1d, /* x, y, uncy    for point 2 */
				777d,	/* uncy for entire shoreline */
				.5 /* intersection location b/t the two points */); 
		assertEquals(777d, intersect.getUncertainty(), COMP_ERROR);
		
	}
}
