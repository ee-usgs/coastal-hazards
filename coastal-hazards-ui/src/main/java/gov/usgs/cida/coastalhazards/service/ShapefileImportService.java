package gov.usgs.cida.coastalhazards.service;

import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.utilities.communication.GeoserverHandler;
import gov.usgs.cida.utilities.communication.GeoserverHandler.DBaseColumn.ColumnType;
import gov.usgs.cida.utilities.communication.RequestResponseHelper;
import gov.usgs.cida.utilities.file.FileHelper;
import gov.usgs.cida.utilities.properties.JNDISingleton;
import it.geosolutions.geoserver.rest.GeoServerRESTManager;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 * TODO:  It does not look like this is used.
 * 
 * @author isuftin
 * @author rhayes
 */
public class ShapefileImportService extends HttpServlet {

    private static final String ENCODED_Z_SUFFIX = gov.usgs.cida.coastalhazards.uncy.UncyZEncoder.ENCODED_Z_SUFFIX;
    
	private static final long serialVersionUID = 1L;
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ShapefileImportService.class);
    private static DynamicReadOnlyProperties props = null;
    private static String uploadDirectory = null;
    private static String geoserverEndpoint = null;
    private static String geoserverUsername = null;
    private static String geoserverPassword = null;
    private static String geoserverDataDir = null;
    private static String DIRECTORY_BASE_PARAM_CONFIG_KEY = "coastal-hazards.files.directory.base";
    private static String DIRECTORY_UPLOAD_PARAM_CONFIG_KEY = "coastal-hazards.files.directory.upload";
    private static String GEOSERVER_ENDPOINT_PARAM_CONFIG_KEY = "coastal-hazards.geoserver.endpoint";
    private static String GEOSERVER_USER_PARAM_CONFIG_KEY = "coastal-hazards.geoserver.username";
    private static String GEOSERVER_PASS_PARAM_CONFIG_KEY = "coastal-hazards.geoserver.password";
    private static String GEOSERVER_DATA_DIR_KEY = "coastal-hazards.geoserver.datadir";
    private static GeoserverHandler geoserverHandler = null;
    private static GeoServerRESTManager gsrm = null;

    @Override
    public void init() throws ServletException {
        super.init();
        props = JNDISingleton.getInstance();
        uploadDirectory = props.getProperty(DIRECTORY_BASE_PARAM_CONFIG_KEY) + props.getProperty(DIRECTORY_UPLOAD_PARAM_CONFIG_KEY);
        geoserverEndpoint = props.getProperty(GEOSERVER_ENDPOINT_PARAM_CONFIG_KEY);
        geoserverUsername = props.getProperty(GEOSERVER_USER_PARAM_CONFIG_KEY);
        geoserverPassword = props.getProperty(GEOSERVER_PASS_PARAM_CONFIG_KEY);
        geoserverDataDir = props.getProperty(GEOSERVER_DATA_DIR_KEY);
        geoserverHandler = new GeoserverHandler(geoserverEndpoint, geoserverUsername, geoserverPassword);
        try {
            gsrm = new GeoServerRESTManager(new URL(geoserverEndpoint), geoserverUsername, geoserverPassword);
        } catch (MalformedURLException ex) {
            Logger.getLogger(ShapefileImportService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, IllegalArgumentException {
        String fileToken = request.getParameter("file-token");
        String featureName = request.getParameter("feature-name");
        String workspace = request.getParameter("workspace");
        String store = request.getParameter("store");
        String[] extraColumns = request.getParameterValues("extra-column");

        File shapeFile;
        String name;
        Map<String, String> responseMap = new HashMap<String, String>();

        List<GeoserverHandler.DBaseColumn> dbcList = new ArrayList<GeoserverHandler.DBaseColumn>();

        if (extraColumns != null) {
            for (String extraColumn : extraColumns) {
                String[] columnParams = extraColumn.trim().split("\\|");
                
                if (columnParams.length < 3 || columnParams.length > 4) {
                    responseMap.put("error", "Extra columns parameter has too few or too many parameters");
                    RequestResponseHelper.sendErrorResponse(response, responseMap);
                    return;
                }
                
                char columnTypeChar = Character.toUpperCase(columnParams[0].charAt(0));
                ColumnType ct;
                String columnName = columnParams[1];
                int fieldLength;
                int decimalCount = columnParams.length == 3 ? 0 : Integer.getInteger(columnParams[3]);
                
                switch (columnTypeChar) {
                    case 'C': {
                        ct = ColumnType.STRING;
                        fieldLength = Integer.getInteger(columnParams[2], 254);
                        break;
                    }
                    case 'N': {
                        ct = ColumnType.NUMERIC;
                        fieldLength = Integer.getInteger(columnParams[2], 18);
                        break;
                    }
                    case 'F': {
                        ct = ColumnType.FLOATING;
                        fieldLength = Integer.getInteger(columnParams[2], 20);
                        break;
                    }
                    case 'L': {
                        ct = ColumnType.LOGICAL;
                        fieldLength = Integer.getInteger(columnParams[2], 1);
                        break;
                    }
                    case 'D': {
                        ct = ColumnType.DATE;
                        fieldLength = Integer.getInteger(columnParams[2], 8);
                        break;
                    }
                    case '@': {
                        ct = ColumnType.TIMESTAMP;
                        fieldLength = Integer.getInteger(columnParams[2], 8);
                        break;
                    }
                    default: {
                        responseMap.put("error", "Column Type must be 'C' (Character), 'N' (Numeric), 'F' (Floating), 'L' (Logical (Boolean)), 'D' (Date) or '@' (Timestamp)");
                        RequestResponseHelper.sendErrorResponse(response, responseMap);
                        return;
                    }
                }
                GeoserverHandler.DBaseColumn dbc = new GeoserverHandler.DBaseColumn(ct, columnName, fieldLength, decimalCount);
                dbcList.add(dbc);
            }
        }

        File fileDirectoryHandle;
        if (StringUtils.isBlank(fileToken)) {
            // If fileToken is blank, create a blank shapefile to import
            fileToken = UUID.randomUUID().toString();
            String fileName = UUID.randomUUID().toString();
            File subdir = new File(uploadDirectory + File.separator + fileToken);
            FileUtils.forceMkdir(subdir);
            File emptyShapeFile = geoserverHandler.createEmptyShapefile(subdir.getPath(), fileName, dbcList);
            FileHelper.zipFile(emptyShapeFile.getParentFile(), null, null);
            fileDirectoryHandle = new File(new File(uploadDirectory), fileToken);
        } else {
            fileDirectoryHandle = new File(new File(uploadDirectory), fileToken);
        }

        if (StringUtils.isBlank(workspace)) {
            responseMap.put("error", "Parameter 'workspace' cannot be blank");
            RequestResponseHelper.sendErrorResponse(response, responseMap);
            return;
        }

        if (!fileDirectoryHandle.exists() || fileDirectoryHandle.listFiles().length == 0) {
            FileUtils.deleteQuietly(fileDirectoryHandle);
            responseMap.put("error", "The file denoted by token " + fileToken + " does not exist.");
            RequestResponseHelper.sendErrorResponse(response, responseMap);
            return;
        }
        try {
            geoserverHandler.prepareWorkspace(geoserverDataDir, workspace);
        } catch (MalformedURLException ex) {
            responseMap.put("error", "Could not create workspace: " + ex.getMessage());
            RequestResponseHelper.sendErrorResponse(response, responseMap);
            return;
        } catch (URISyntaxException ex) {
            responseMap.put("error", "Could not create workspace: " + ex.getMessage());
            RequestResponseHelper.sendErrorResponse(response, responseMap);
            return;
        }

        FileFilter zipFileFilter = new WildcardFileFilter("*.zip");

        // Upload directory might contain both input shapefile and shapefile_pts
        File[] zipFiles = fileDirectoryHandle.listFiles(zipFileFilter);
        File pts_File = null;
        shapeFile = null;
        for (File file : zipFiles) {
        	if (file.getName().endsWith(ENCODED_Z_SUFFIX + ".zip")) {
        		pts_File = file;
        	} else {
        		shapeFile = file;
        	}
        }
        
		if (shapeFile == null) {
            responseMap.put("error", "No input located");
            RequestResponseHelper.sendErrorResponse(response, responseMap);
            return;
		}
		
        responseMap.put("file-token", fileToken);
        responseMap.put("endpoint", geoserverEndpoint);

        name = StringUtils.isBlank(featureName) ? FilenameUtils.removeExtension(shapeFile.getName()) : featureName;

        if (pts_File != null) {
        	String pts_name = name + ENCODED_Z_SUFFIX;
            HttpResponse pts_importResponse = geoserverHandler.importFeaturesFromFile(pts_File, workspace, store, pts_name);
            String pts_responseText = IOUtils.toString(pts_importResponse.getEntity().getContent());

            if (!pts_responseText.toLowerCase().contains("ows:exception")) {
                responseMap.put("pts_feature", pts_responseText);
            } else {
                InputSource source = new InputSource(new StringReader(pts_responseText));
                XPath xPath = XPathFactory.newInstance().newXPath();
                NodeList list;
                String error = "";
                try {
                    list = (NodeList) xPath.evaluate("//*[local-name()='ExceptionText']", source, XPathConstants.NODESET);
                    error = list.item(0).getTextContent();
                } catch (XPathExpressionException ex) {
                    Logger.getLogger(ShapefileImportService.class.getName()).log(Level.SEVERE, null, ex);
                }

                responseMap.put("pts_error", error);
            }
        }

        HttpResponse importResponse = geoserverHandler.importFeaturesFromFile(shapeFile, workspace, store, name);
        String responseText = IOUtils.toString(importResponse.getEntity().getContent());

        if (!responseText.toLowerCase().contains("ows:exception")) {
            responseMap.put("feature", responseText);
            RequestResponseHelper.sendSuccessResponse(response, responseMap);
        } else {
            InputSource source = new InputSource(new StringReader(responseText));
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList list;
            String error = "";
            try {
                list = (NodeList) xPath.evaluate("//*[local-name()='ExceptionText']", source, XPathConstants.NODESET);
                error = list.item(0).getTextContent();
            } catch (XPathExpressionException ex) {
                Logger.getLogger(ShapefileImportService.class.getName()).log(Level.SEVERE, null, ex);
            }

            responseMap.put("error", error);
            RequestResponseHelper.sendErrorResponse(response, responseMap);
        }
        
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}
