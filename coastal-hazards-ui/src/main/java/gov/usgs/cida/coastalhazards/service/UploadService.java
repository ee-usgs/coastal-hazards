package gov.usgs.cida.coastalhazards.service;

import gov.usgs.cida.geoutils.geoserver.servlet.ShapefileUploadServlet;
import gov.usgs.cida.utilities.file.FileHelper;
import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.servlet.ServletException;
import org.hsqldb.lib.FileUtil;

public class UploadService extends ShapefileUploadServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void init() throws ServletException {
        super.init();
    }

	@Override
	protected File transformUploadedFile(File shapefileZip) throws Exception {
		Enumeration<? extends ZipEntry> entries = new ZipFile(shapefileZip).entries();
        boolean hasHidden = false;
        boolean needsLidarExplosion = false;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (FileHelper.entryIsHidden(entry)) {
                hasHidden = true;
            }
            if (entry.getName().endsWith("_uncertainty.dbf")) {
            	needsLidarExplosion = true;
            }
        }
        if (hasHidden) {
            FileHelper.removeHiddenEntries(shapefileZip);
        }
        
        if (needsLidarExplosion) {
        	// add another zipfile to the upload directory, with exploded point data
        	ZipInterpolator zEncoder = new ZipInterpolator();
        	
        	try {
        		File zEncodedFile = zEncoder.explode(shapefileZip);
				FileUtil.delete(shapefileZip.getAbsolutePath());
        		
				return zEncodedFile;

			} catch (Exception e) {
				throw new RuntimeException("Problem exploding shapefile zip file", e);
			}
        } else {
			return shapefileZip;
		}
	}

}
