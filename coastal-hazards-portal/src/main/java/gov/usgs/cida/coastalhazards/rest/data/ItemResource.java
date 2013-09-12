package gov.usgs.cida.coastalhazards.rest.data;

import com.google.gson.Gson;
import gov.usgs.cida.coastalhazards.jpa.ItemManager;
import gov.usgs.cida.coastalhazards.model.Item;
import gov.usgs.cida.coastalhazards.model.summary.Summary;
import gov.usgs.cida.config.DynamicReadOnlyProperties;
import gov.usgs.cida.utilities.properties.JNDISingleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.PathParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Path;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Could also be called item or layer or some other way of describing a singular
 * thing
 *
 * @author jordan
 */
@Path("item")
public class ItemResource {

	@Context
	private UriInfo context;
	private Gson gson = new Gson();
	private static ItemManager itemManager;
    private static String cchn52Endpoint;
    private static final DynamicReadOnlyProperties props;

	static {
        props = JNDISingleton.getInstance();
        cchn52Endpoint = props.getProperty("coastal-hazards.n52.endpoint");
		itemManager = new ItemManager();
	}

	/**
	 * Retrieves representation of an instance of
	 * gov.usgs.cida.coastalhazards.rest.TestResource
	 *
	 * @param id
	 * @return an instance of java.lang.String
	 */
	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getCard(@PathParam("id") String id) {
		String jsonResult = itemManager.load(id);
		Response response;
		if (null == jsonResult) {
			response = Response.status(Response.Status.NOT_FOUND).build();
		} else {
			response = Response.ok(jsonResult, MediaType.APPLICATION_JSON_TYPE).build();
		}
		return response;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response searchCards(
            @DefaultValue("") @QueryParam("query") List<String> query,
            @DefaultValue("") @QueryParam("type") List<String> type,
			@DefaultValue("popularity") @QueryParam("sortBy") String sortBy,
			@DefaultValue("-1") @QueryParam("count") int count,
			@DefaultValue("") @QueryParam("bbox") String bbox) {
		// need to figure out how to search popularity and bbox yet
		String jsonResult = itemManager.query(query, type, sortBy, count, bbox);
		return Response.ok(jsonResult, MediaType.APPLICATION_JSON_TYPE).build();
	}

	/**
	 * Only allows one card to be posted at a time for now
	 *
	 * @param content
	 * @return
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response postCard(String content, @Context HttpServletRequest request) {
        Response response;
        HttpSession session = request.getSession();
        if (session == null) {
            response = Response.status(Response.Status.BAD_REQUEST).build();
        } else {
            Boolean valid = (session.getAttribute("sessionValid") == null) ? false : (Boolean)session.getAttribute("sessionValid");
            if (valid) {
                final String id = itemManager.save(content);

                if (null == id) {
                    response = Response.status(Response.Status.BAD_REQUEST).build();
                } else {
                    Map<String, Object> ok = new HashMap<String, Object>() {
                        private static final long serialVersionUID = 2398472L;
                        {
                            put("id", id);
                        }
                    };
                    response = Response.ok(new Gson().toJson(ok, HashMap.class), MediaType.APPLICATION_JSON_TYPE).build();
                }
            } else {
                response = Response.status(Response.Status.UNAUTHORIZED).build();
            }
        }
		return response;
	}

	@POST
	@Path("/preview")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response publishPreviewCard(String content) {
        Response response = Response.serverError().build();
        
        Item item = Item.fromJSON(content);
        try {
            String jsonSummary = getSummaryFromWPS(item.getMetadata(), item.getAttr());
            // this is not actually summary json object, so we need to change that a bit
            Summary summary = gson.fromJson(jsonSummary, Summary.class);
            item.setSummary(summary);
        } catch (Exception ex) {
            Map<String,String> err = new HashMap<String, String>();
            err.put("message", ex.getMessage());
            response = Response.serverError().entity(new Gson().toJson(err, HashMap.class)).build();
        }
        if (item.getSummary() != null) {
            final String id = itemManager.savePreview(item);

            if (null == id) {
                response = Response.status(Response.Status.BAD_REQUEST).build();
            } else {
                Map<String, String> ok = new HashMap<String, String>() {
                    private static final long serialVersionUID = 23918472L;

                    {
                        put("id", id);
                    }
                };
                response = Response.ok(new Gson().toJson(ok, HashMap.class), MediaType.APPLICATION_JSON_TYPE).build();
            }
        }
		return response;
	}
    
    private String getSummaryFromWPS(String metadataId, String attr) throws IOException, ParserConfigurationException, SAXException {
        MetadataResource metadata = new MetadataResource();
        Response response = metadata.getFileById(metadataId);
        String xmlWithoutHeader = response.getEntity().toString().replaceAll("<\\?xml[^>]*>", "");
        String wpsRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<wps:Execute xmlns:wps=\"http://www.opengis.net/wps/1.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs\" xmlns:ows=\"http://www.opengis.net/ows/1.1\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" service=\"WPS\" version=\"1.0.0\" xsi:schemaLocation=\"http://www.opengis.net/wps/1.0.0 http://schemas.opengis.net/wps/1.0.0/wpsExecute_request.xsd\">"
                    + "<ows:Identifier>org.n52.wps.server.r.item.summary</ows:Identifier>"
                    + "<wps:DataInputs>"
                    + "<wps:Input>"
                    + "<ows:Identifier>input</ows:Identifier>"
                    + "<wps:Data>"
                    + "<wps:ComplexData mimeType=\"text/xml\">"
                    + xmlWithoutHeader
                    + "</wps:ComplexData>"
                    + "</wps:Data>"
                    + "</wps:Input>"
                    + "<wps:Input>"
                    + "<ows:Identifier>attr</ows:Identifier>"
                    + "<wps:Data>"
                    + "<wps:LiteralData>" + attr + "</wps:LiteralData>"
                    + "</wps:Data>"
                    + "</wps:Input>"
                    + "</wps:DataInputs>"
                    + "<wps:ResponseForm>"
                    + "<wps:RawDataOutput>"
                    + "<ows:Identifier>output</ows:Identifier>"
                    + "</wps:RawDataOutput>"
                    + "</wps:ResponseForm>"
                    + "</wps:Execute>";
            HttpUriRequest req = new HttpPost(cchn52Endpoint + "/WebProcessingService");
            HttpClient client = new DefaultHttpClient();
            req.addHeader("Content-Type", "text/xml");
            if (!StringUtils.isBlank(wpsRequest) && req instanceof HttpEntityEnclosingRequestBase) {
                StringEntity contentEntity = new StringEntity(wpsRequest);
                ((HttpEntityEnclosingRequestBase) req).setEntity(contentEntity);
            }
            HttpResponse resp = client.execute(req);
            StatusLine statusLine = resp.getStatusLine();

            if (statusLine.getStatusCode() != 200) {
                throw new IOException("Error in response from wps");
            }
            String data = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
            if (data.contains("ExceptionReport")) {
                String error = "Error in response from wps";
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(data.getBytes()));
                JXPathContext ctx = JXPathContext.newContext(doc.getDocumentElement());
                ctx.registerNamespace("ows", "http://www.opengis.net/ows/1.1");
                List<Node> nodes = ctx.selectNodes("ows:Exception/ows:ExceptionText/text()");
                if (nodes != null && !nodes.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    for (Node node : nodes) {
                        builder.append(node.getTextContent()).append(System.lineSeparator());
                    }
                    error = builder.toString();
                }
                throw new RuntimeException(error);
            }
            return data;
    }
}
