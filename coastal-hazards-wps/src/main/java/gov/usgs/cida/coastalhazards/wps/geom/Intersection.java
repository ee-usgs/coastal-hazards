/*
 * U.S.Geological Survey Software User Rights Notice
 * 
 * Copied from http://water.usgs.gov/software/help/notice/ on September 7, 2012.  
 * Please check webpage for updates.
 * 
 * Software and related material (data and (or) documentation), contained in or
 * furnished in connection with a software distribution, are made available by the
 * U.S. Geological Survey (USGS) to be used in the public interest and in the 
 * advancement of science. You may, without any fee or cost, use, copy, modify,
 * or distribute this software, and any derivative works thereof, and its supporting
 * documentation, subject to the following restrictions and understandings.
 * 
 * If you distribute copies or modifications of the software and related material,
 * make sure the recipients receive a copy of this notice and receive or can get a
 * copy of the original distribution. If the software and (or) related material
 * are modified and distributed, it must be made clear that the recipients do not
 * have the original and they must be informed of the extent of the modifications.
 * 
 * For example, modified files must include a prominent notice stating the 
 * modifications made, the author of the modifications, and the date the 
 * modifications were made. This restriction is necessary to guard against problems
 * introduced in the software by others, reflecting negatively on the reputation of the USGS.
 * 
 * The software is public property and you therefore have the right to the source code, if desired.
 * 
 * You may charge fees for distribution, warranties, and services provided in connection
 * with the software or derivative works thereof. The name USGS can be used in any
 * advertising or publicity to endorse or promote any products or commercial entity
 * using this software if specific written permission is obtained from the USGS.
 * 
 * The user agrees to appropriately acknowledge the authors and the USGS in publications
 * that result from the use of this software or in products that include this
 * software in whole or in part.
 * 
 * Because the software and related material are free (other than nominal materials
 * and handling fees) and provided "as is," the authors, the USGS, and the 
 * United States Government have made no warranty, express or implied, as to accuracy
 * or completeness and are not obligated to provide the user with any support, consulting,
 * training or assistance of any kind with regard to the use, operation, and performance
 * of this software nor to provide the user with any updates, revisions, new versions or "bug fixes".
 * 
 * The user assumes all risk for any damages whatsoever resulting from loss of use, data,
 * or profits arising in connection with the access, use, quality, or performance of this software.
 */
package gov.usgs.cida.coastalhazards.wps.geom;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.strtree.STRtree;
import gov.usgs.cida.coastalhazards.util.AttributeGetter;
import static gov.usgs.cida.coastalhazards.util.Constants.*;
import gov.usgs.cida.coastalhazards.wps.exceptions.UnsupportedFeatureTypeException;
import gov.usgs.cida.coastalhazards.wps.exceptions.UnsupportedValueException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.GeometryType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class Intersection {

    private Point intersectPoint;
    private double distance;
    private SimpleFeature feature;
	private LineString segment;
    private int transectId;
    private AttributeGetter attGet;
    private static DateTimeFormatter inputFormat;
    private static DateTimeFormatter outputFormat;

    static {
        try {
            inputFormat = new DateTimeFormatterBuilder()
                    .appendMonthOfYear(2)
                    .appendLiteral('/')
                    .appendDayOfMonth(2)
                    .appendLiteral('/')
                    .appendYear(4, 4)
                    .toFormatter();

            outputFormat = new DateTimeFormatterBuilder()
                    .appendYear(4, 4)
                    .appendLiteral('-')
                    .appendMonthOfYear(2)
                    .appendLiteral('-')
                    .appendDayOfMonth(2)
                    .toFormatter();
        } catch (Exception ex) {
            // log severe
        }
    }

    /**
     * Stores Intersections from feature for delivery to R
     *
     * @param dist distance from reference (negative for seaward baselines)
     * @param t Assumed to be in format mm/dd/yyyy
     * @param uncy Uncertainty measurement
     * @throws ParseException if date is in wrong format
     */
	
	/**
	 * 
	 * @param intersectPoint cross-point of the transect and the shoreline segment
	 * @param distAlongTransect distance from the transect origen to the intersectPoint (negative for seaward baselines).
	 * @param shoreline SimpleFeature that contained the shoreline geometry (may contain uncy attribute).
	 * @param segment LineString of two coordinates that the transect intersects.  Sub-segment of shoreline geometry.
	 * @param transectId Id of the transect that intersects the segment.
	 * @param getter Provides a aggregated view of the attributes of this Intersection.
	 */
    public Intersection(Point intersectPoint, double distAlongTransect,
			SimpleFeature shoreline, LineString segment, int transectId, AttributeGetter getter) {
        this.intersectPoint = intersectPoint;
        this.distance = distAlongTransect;
        this.feature = shoreline;
		this.segment = segment;
        this.transectId = transectId;
        this.attGet = getter;
    }

    /**
     * Get an intersection object from Intersection Feature Type
     *
     * @param intersectionFeature
     */
    public Intersection(SimpleFeature intersectionFeature, AttributeGetter getter) {
        this.intersectPoint = (Point) intersectionFeature.getDefaultGeometry();
        this.attGet = getter;
        this.transectId = (Integer) attGet.getValue(TRANSECT_ID_ATTR, intersectionFeature);
        this.distance = (Double) attGet.getValue(DISTANCE_ATTR, intersectionFeature);
        this.feature = intersectionFeature;
		this.segment = null;
    }
	
	/**
	 * Creates a SimpleFeatureType for Intersections, nominally based on the 
	 * shoreline SimpleFeatureCollection.  Attribute types are copied with these
	 * exceptions:
	 * <ul>
	 * <li>The first attribute is 'geom' of type Point (Any other geom is removed)</li>
	 * <li>TransectID (Integer) is added</li>
	 * <li>DISTANCE_ATTR (Double) is added</li>
	 * </ul>
	 * All other attributes are copied over including the 'uncy' attribute, 
	 * although it is not handled specifically.
	 * 
	 * @param collection SimpleFeatureCollection to copy the schema from.
	 * @param crs CoordinateReferenceSystem for the geometry.
	 * 
	 * @return Type to be used for intersections. 
	 */
    public static SimpleFeatureType buildSimpleFeatureType(SimpleFeatureCollection collection, CoordinateReferenceSystem crs) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        SimpleFeatureType schema = collection.getSchema();
        List<AttributeType> types = schema.getTypes();

        builder.setName("Intersections");
        builder.add("geom", Point.class, crs);
        builder.add(TRANSECT_ID_ATTR, Integer.class);
        builder.add(DISTANCE_ATTR, Double.class);
		builder.add(UNCY_ATTR, Double.class);
        for (AttributeType type : types) {
            if (type instanceof GeometryType) {
                // ignore the geom type of intersecting data
			} else if (type.getName() != null && type.getName().getLocalPart() != null &&
					UNCY_ATTR.equalsIgnoreCase(type.getName().getLocalPart())) {
				//Ignore - we already added this type.
            } else {
                builder.add(type.getName().getLocalPart(), type.getBinding());
            }
        }
        return builder.buildFeatureType();
    }

	/**
	 * Create the SimpleFeature-Point representation of this intersection.
	 * Attribute values are copied from either the shorelineReach (MultiLine)
	 * or the intersectionFeature (Point), depending on the constructor.  Values
	 * are copied except as follows:
	 * <ul>
	 * <li>The geom is assigned as this.point (constructor dependent point or intersectionFeature)</li>
	 * <li>TransectID (Integer) is assigned transectId</li>
	 * <li>DISTANCE_ATTR (Double) is assigned distance</li>
	 * <li>UNCY_ATTR (Double) is assigned the uncertainty from either the line or interpolated from the point.</li>
	 * </ul>
	 * 
	 * @param type The type used for (created by buildSimpleFeatureType)
	 * @return 
	 */
    public SimpleFeature createFeature(SimpleFeatureType type) {
        List<AttributeType> types = type.getTypes();
        Object[] featureObjectArr = new Object[types.size()];
        for (int i = 0; i < featureObjectArr.length; i++) {
            AttributeType attrType = types.get(i);
            if (attrType instanceof GeometryType) {
                featureObjectArr[i] = intersectPoint;
            } else if (attGet.matches(attrType.getName(), TRANSECT_ID_ATTR)) {
                featureObjectArr[i] = new Integer(transectId);
            } else if (attGet.matches(attrType.getName(), DISTANCE_ATTR)) {
                featureObjectArr[i] = new Double(distance);
			} else if (attGet.matches(attrType.getName(), UNCY_ATTR)) {
				featureObjectArr[i] = getUncertainty();
            } else {
                featureObjectArr[i] = this.feature.getAttribute(attrType.getName());
            }
        }
        return SimpleFeatureBuilder.build(type, featureObjectArr, null);
    }
	
	/**
	 * Attempt to calculate the uncertainty from individual point uncertainty for this intersection.
	 * 
	 * If this is a zero-length segment, the lower of the two uncertainties is
	 * returned.
	 * 
	 * @return The uncertainty value based on individual point uncertainties or
	 *			null if individual point uncies are not available.
	 * @throws UnsupportedValueException if the uncertainty value is negative.
	 */
	private Double calculatePointBasedUncertainty() throws UnsupportedValueException {
		Double uncy = null;
		
		
		if (segment != null) {
			Coordinate c1 = segment.getCoordinateN(0);
			Coordinate c2 = segment.getCoordinateN(1);
			
			if (! (Double.isNaN(c1.z) || Double.isNaN(c2.z))) {

				if (c1.z < 0d || c2.z < 0d) {
					throw new UnsupportedValueException(
							"Point uncertainties cannot be less than zero");
				}
				
				//We have individual point uncertainties.
				//
				//Linear weighted mean Uncertainty (Um) calc:
				//d1, d2= distance from c1 or c2 to the intersection
				//dt = total distance (d1 + d2)
				//u1 / u2 = uncertainties, respectively
				//
				//Um = ( (d2 * u1) + (d1 * u2) ) / dt
				//

				double d1 = c1.distance(intersectPoint.getCoordinate());
				double d2 = c2.distance(intersectPoint.getCoordinate());
				double dt = d1 + d2;
				
				if (dt > 0) {
					//Normal segment
					uncy = ((d2 * c1.z) + (d1 * c2.z)) / dt;
				} else {
					//Zero length segment - take the lowest of the uncy values
					//(we have two measurements, one of which we have more confidence in)
					uncy = Math.min(c1.z, c2.z);
				}
				
				
			}
		}
		
		return uncy;
	}

    public DateTime getDate() {
        Object date = attGet.getValue(DATE_ATTR, this.feature);
        if (date instanceof Date) {
            return new DateTime((Date) date);
        } else if (date instanceof String) {
            DateTime datetime = inputFormat.parseDateTime((String) date);
            return datetime;
        } else {
            throw new UnsupportedFeatureTypeException("Not sure what to do with date");
        }
    }

	/**
	 * Find the uncertainty for this intersection.
	 * 
	 * There are two cases which are detected and handled automatically:
	 * <ol>
	 * <li>There is uncertainty associate w/ each point - use calculatePointBasedUncertainty()</li>
	 * <li>There is no point-based uncertainty - use the feature-wide value</li>
	 * </ol>
	 * @return 
	 */
    public double getUncertainty() throws UnsupportedFeatureTypeException, UnsupportedValueException {
		Double uncy = calculatePointBasedUncertainty();
		
		if (uncy == null) {
			Object uncyObj = attGet.getValue(UNCY_ATTR, this.feature);
			if (uncyObj instanceof Double) {
				uncy = (Double) uncyObj;
			} else {
				throw new UnsupportedFeatureTypeException("Uncertainty should be a double");
			}
		}
		
		return uncy;
    }

    public int getTransectId() {
        return transectId;
    }
    
    public double getDistance() {
        return distance;
    }

    /**
     * Returns the desired intersection
     *
     * @param a first intersection
     * @param b second intersection
     * @param closest return the closest intersection (false for farthest)
     * @return Intersection
     */
    public static Intersection compare(Intersection a, Intersection b, boolean closest) {
        boolean aFarther = ((Math.abs(a.distance) - Math.abs(b.distance)) > 0);
        if (closest) {
            return (aFarther) ? b : a;
        } else {
            return (aFarther) ? a : b;
        }
    }

    public static double absoluteFarthest(double min, Collection<Intersection> intersections) {
        double maxVal = min;
        for (Intersection intersection : intersections) {
            double absDist = Math.abs(intersection.distance);
            if (absDist > maxVal) {
                maxVal = absDist;
            }
        }
        return maxVal;
    }

    public static Map<DateTime, Intersection> calculateIntersections(Transect transect, STRtree strTree, boolean useFarthest, AttributeGetter getter) {
        Map<DateTime, Intersection> allIntersections = new HashMap<DateTime, Intersection>();
        LineString line = transect.getLineString();
        List<ShorelineFeature> possibleIntersects = strTree.query(line.getEnvelopeInternal());
        for (ShorelineFeature shoreline : possibleIntersects) {
            LineString segment = shoreline.segment;
            if (segment.intersects(line)) {
                // must be a point
                Point crossPoint = (Point) segment.intersection(line);
                Orientation orientation = transect.getOrientation();
                double distance = orientation.getSign()
                        * transect.getOriginCoord()
                        .distance(crossPoint.getCoordinate());
                Intersection intersection =
                        new Intersection(crossPoint, distance, shoreline.feature, segment, transect.getId(), getter);
                DateTime date = intersection.getDate();
                if (allIntersections.containsKey(date)) {  // use closest/farthest intersection
                    Intersection thatIntersection = allIntersections.get(date);
                    Intersection closest = Intersection.compare(intersection, thatIntersection, !useFarthest);
                    allIntersections.put(date, closest);
                } else {
                    allIntersections.put(date, intersection);
                }
            }
        }
        return allIntersections;
    }
    
    /**
     * Map is mutated to include new intersections in section of transect called here "subTransect"
     * 
     * @param intersectionsSoFar
     * @param origin
     * @param subTransect
     * @param strTree
     * @param useFarthest
     * @param getter 
     */
    public static void updateIntersectionsWithSubTransect(Map<DateTime, Intersection> intersectionsSoFar, Point origin,
            Transect subTransect, STRtree strTree, boolean useFarthest, AttributeGetter getter) {
        Map<DateTime, Intersection> intersectionSubset = calculateIntersections(subTransect, strTree, useFarthest, getter);
        for (DateTime date : intersectionSubset.keySet()) {
            Intersection intersection = intersectionSubset.get(date);
            intersection.distance = subTransect.getOrientation().getSign() * intersection.intersectPoint.distance(origin);
            if (intersectionsSoFar.containsKey(date)) {
                boolean isFarther = Math.abs(intersection.distance) > Math.abs(intersectionsSoFar.get(date).distance);
                // only true  && true
                // or   false && false
                if (useFarthest == isFarther) {
                    intersectionsSoFar.put(date, intersection);
                }
            }
            else {
                intersectionsSoFar.put(date, intersection);
            }
        }
    }

    @Override
    public String toString() {
        String time = outputFormat.print(getDate());
        double uncertainty = getUncertainty();
        String str = time + "\t" + distance + "\t" + uncertainty;
        return str;
    }
}
