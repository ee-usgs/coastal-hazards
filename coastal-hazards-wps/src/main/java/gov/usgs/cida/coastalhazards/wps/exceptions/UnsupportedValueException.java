package gov.usgs.cida.coastalhazards.wps.exceptions;

/**
 * For zero or negative values where they are not expected.
 * @author Eric Everman <eeverman@usgs.gov>
 */
public class UnsupportedValueException extends RuntimeException {

    public UnsupportedValueException() {
        super();
    }

    public UnsupportedValueException(String message) {
        super(message);
    }
    
}
