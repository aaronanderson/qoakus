package com.github.aaronanderson.qoakus;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Calendar;
import java.util.TimeZone;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.jcr.Binary;
import javax.jcr.LoginException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import javax.jcr.security.Privilege;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.security.auth.Subject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.spi.security.authentication.SystemSubject;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import com.github.aaronanderson.qoakus.oidc.OIDCTokenCredentials;

import io.quarkus.oidc.IdToken;

@Path("/content")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContentRS {

    static Logger logger = Logger.getLogger(ContentRS.class);

    @Inject
    Repository repository;

    //Don't use the Quarkus custom parser, use the standard one for more control.
    //@Inject
    //TikaParser parser;

    @Inject
    @IdToken
    Instance<JsonWebToken> idToken;

    private Session getSession(String path) throws LoginException, RepositoryException, PrivilegedActionException {
        if ("/".equals(path)) {
            //adding children to the root node is tricky because the oakus-general group has limited access,  excluding JCR_MODIFY_ACCESS_CONTROL and JCR_MODIFY_PROPERTIES while originally including 
            //JCR_ADD_CHILD_NODES, JCR_REMOVE_CHILD_NODES and JCR_NODE_TYPE_MANAGEMENT. Since the JCR_MODIFY_PROPERTIES permission is not inherited to the new nodes required properties cannot be set 
            //and the oakus-general members would not be able to commit node updates. When adding file attachments to the root node change users to the super admin user and add an ACL on the file giving full
            //access to the group.                          
            return Subject.doAs(SystemSubject.INSTANCE, (PrivilegedExceptionAction<Session>) () -> (repository).login(null, null));
        }
        return getSession(idToken, repository);
    }

    static Session getSession(Instance<JsonWebToken> idToken, Repository repository) throws LoginException, RepositoryException {
        if (idToken.isResolvable()) {
            Session session = repository.login(new OIDCTokenCredentials(idToken.get()));
            //RepositoryManager.saveRepositoryXML(repository);
            return session;
        }
        return repository.login(new SimpleCredentials("test", "test".toCharArray()));
    }

    //not sure why two different resources are needed. Couldn't get a single regexp to work.
    @GET
    @Path("/")
    public Response rootViewContent(@PathParam("contentPath") String contentPath) {
        return viewContent("");
    }

    @GET
    @Path("/{contentPath:.*}")
    public Response viewContent(@PathParam("contentPath") String contentPath) {
        try {
            JsonObjectBuilder result = Json.createObjectBuilder();
            JsonArrayBuilder files = Json.createArrayBuilder();
            JsonArrayBuilder children = Json.createArrayBuilder();
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;

            //always use the user's session for read-only access
            Session session = getSession(idToken, repository);
            Node node = session.getNode(contentPath);

            if (contentPath.length() > 1) {
                Node parent = node.getParent();
                JsonObjectBuilder content = Json.createObjectBuilder();
                content.add("path", parent.getPath());
                content.add("title", parent.getProperty("qo:title").getString());
                result.add("parent", content);
            }

            NodeIterator nodeIterator = node.getNodes();
            while (nodeIterator.hasNext()) {
                Node child = nodeIterator.nextNode();
                if (child.isNodeType("qo:content")) {
                    JsonObjectBuilder content = Json.createObjectBuilder();
                    content.add("path", child.getPath());
                    content.add("title", child.getProperty("qo:title").getString());
                    children.add(content);
                } else if (child.isNodeType(JcrConstants.NT_FILE)) {
                    JsonObjectBuilder file = Json.createObjectBuilder();
                    if (child.isNodeType("qo:file")) {
                        file.add("fileType", child.getProperty("qo:fileType").getString());
                    }
                    Node ntResource = child.getNode(JcrConstants.JCR_CONTENT);
                    file.add("name", child.getName());
                    String mimeType = ntResource.getProperty(JcrConstants.JCR_MIMETYPE).getString();
                    file.add("mimeType", mimeType);
                    Calendar lastModified = ntResource.getProperty(JcrConstants.JCR_LASTMODIFIED).getDate();
                    TimeZone tz = lastModified.getTimeZone();
                    ZoneId zid = tz == null ? ZoneId.systemDefault() : tz.toZoneId();
                    ZonedDateTime lastModifiedTime = ZonedDateTime.ofInstant(lastModified.toInstant(), zid);
                    file.add("lastModified", lastModifiedTime.format(DateTimeFormatter.ISO_INSTANT));
                    files.add(file);
                }
            }
            result.add("files", files);
            result.add("children", children);
            result.add("title", node.getProperty("qo:title").getString());
            result.add("path", contentPath);
            result.add("status", "ok");
            return Response.status(200).entity(result.build()).build();

        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @POST
    @Path("/")
    public Response newContent(JsonObject request) {
        return newContent("", request);
    }

    @POST
    @Path("{parentContentPath:.*}")
    public Response newContent(@PathParam("parentContentPath") String parentContentPath, JsonObject request) {
        try {
            String path = parentContentPath.startsWith("/") ? parentContentPath : "/" + parentContentPath;
            Session session = getSession(path);
            Node parentNode = session.getNode(path);
            Node category = parentNode.addNode(RandomStringUtils.random(10, true, true));
            category.addMixin("qo:content");
            if (request.containsKey("title")) {
                category.setProperty("qo:title", request.getString("title"));
            }
            UserManager userManager = ((JackrabbitSession) session).getUserManager();
            Group general = userManager.getAuthorizable("qoakus-general", Group.class);
            AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);
            session.save();
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok").add("newPath", category.getPath()).build()).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @PUT
    @Path("{contentPath:.*}")
    public Response editContent(@PathParam("contentPath") String contentPath, JsonObject request) {
        try {
            String path = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(path);
            Node node = session.getNode(path);
            if (!"/".equals(path) && request.containsKey("title")) {
                node.setProperty("qo:title", request.getString("title"));
            }
            session.save();
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok").build()).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @DELETE
    @Path("{contentPath:.*}")
    public Response deleteContent(@PathParam("contentPath") String contentPath) {
        try {
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(contentPath);
            Node node = session.getNode(contentPath);
            node.remove();
            session.save();
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok")).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @GET
    @Path("/file{contentPath:.*}/{fileName}")
    public Response fileDownload(@PathParam("contentPath") String contentPath, @PathParam("fileName") String fileName) {
        try {
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(contentPath);
            Node node = session.getNode(contentPath);
            if (node.hasNode(fileName)) {
                Node ntFile = node.getNode(fileName);
                Node ntResource = ntFile.getNode(JcrConstants.JCR_CONTENT);
                String mimeType = ntResource.getProperty(JcrConstants.JCR_MIMETYPE).getString();
                Binary binary = ntResource.getProperty(JcrConstants.JCR_DATA).getBinary();
                ResponseBuilder response = Response.status(Response.Status.OK);
                response.header("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
                // https://stackoverflow.com/a/49286437
                response.header("Access-Control-Expose-Headers", "Content-Disposition");
                //Not sure why a BufferedInputStream is necessary here since the Oak node segment should be cached upstream but it does boost browser download performance.
                response.entity(new BufferedInputStream(binary.getStream()));
                response.type(mimeType);
                return response.build();
            }
            return Response.status(Response.Status.NOT_FOUND).entity(String.format("Content %s file %s unavailable", contentPath, fileName)).build();

        } catch (Exception e) {
            logger.error("", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(String.format("Content %s file %s retrieval error: %s", contentPath, fileName, e.getMessage())).build();
        }
    }

    @GET
    @Path("/raw{contentPath:.*}/{fileName}")
    public Response fileRaw(@PathParam("contentPath") String contentPath, @PathParam("fileName") String fileName) {
        try {
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(contentPath);
            Node node = session.getNode(contentPath);
            if (node.hasNode(fileName)) {
                Node ntFile = node.getNode(fileName);
                Node ntResource = ntFile.getNode(JcrConstants.JCR_CONTENT);
                //String mimeType = ntResource.getProperty(JcrConstants.JCR_MIMETYPE).getString();
                Binary binary = ntResource.getProperty(JcrConstants.JCR_DATA).getBinary();

                ResponseBuilder response = Response.status(Response.Status.OK);
                //ContentHandlerFactory factory = new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1);
                //RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(factory);
                //ToHTMLContentHandler handler = new ToHTMLContentHandler();
                //String text = parser.getText(binary.getStream(), handler);
                StreamingOutput stream = (os) -> {
                    try {
                        parseFile(new BufferedInputStream(binary.getStream()), os);
                    } catch (RepositoryException | SAXException | TikaException e) {
                        throw new IOException(e);
                    }
                };
                response.entity(stream);
                response.type("text/html");
                return response.build();
            }
            return Response.status(Response.Status.NOT_FOUND).entity(String.format("Content %s file %s unavailable", contentPath, fileName)).build();

        } catch (Exception e) {
            logger.error("", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(String.format("Content %s file %s retrieval error: %s", contentPath, fileName, e.getMessage())).build();
        }
    }

    @POST
    @Path("/search")
    public Response search(JsonObject request) {
        try {
            //always use the user's session for read-only search
            Session session = getSession(idToken, repository);
            JsonArrayBuilder searchResults = Json.createArrayBuilder();

            String queryText = "SELECT [jcr:path], [qo:title] FROM [qo:content] AS c WHERE c.[qo:title] like $likeText or contains(c.*, $containsText)";
            //queryText = "EXPLAIN " + queryText;

            QueryManager qm = session.getWorkspace().getQueryManager();
            Query query = qm.createQuery(queryText, Query.JCR_SQL2);
            ValueFactory valueFactory = session.getValueFactory();
            String searchText = request.getString("value");
            searchText = searchText.endsWith("%") ? searchText : searchText;
            query.bindValue("likeText", valueFactory.createValue("%" + searchText + "%"));
            query.bindValue("containsText", valueFactory.createValue(searchText));
            QueryResult results = query.execute();
            RowIterator iterator = results.getRows();
            while (iterator.hasNext()) {
                Row row = iterator.nextRow();
                if (queryText.startsWith("EXPLAIN")) {
                    logger.infof("Query Explaination: %s\n%s", row);
                } else {
                    JsonObjectBuilder rowJson = Json.createObjectBuilder();
                    rowJson.add("title", row.getValue("qo:title").toString());
                    rowJson.add("path", row.getValue(JcrConstants.JCR_PATH).getString());
                    searchResults.add(rowJson);
                }
            }
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok").add("results", searchResults).build()).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    //supported MIME types - https://github.com/apache/tika/blob/main/tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
    //Don't use the Quarkus Tika parser, directly configure it as needed instead.
    private void parseFile(InputStream is, OutputStream os) throws IOException, SAXException, TikaException {
        ParseContext context = new ParseContext();

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        pdfConfig.setExtractUniqueInlineImagesOnly(true);
        context.set(PDFParserConfig.class, pdfConfig);

        //use the POI SAX parser for better streaming support.
        OfficeParserConfig ooConfig = new OfficeParserConfig();
        ooConfig.setUseSAXDocxExtractor(true);
        context.set(OfficeParserConfig.class, ooConfig);

        context.set(EmbeddedDocumentExtractor.class, new AttachmentEmbeddedDocumentExtractor(context));

        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        context.set(Parser.class, parser);

        ToHTMLContentHandler handler = new ToHTMLContentHandler(os, "UTF-8");
        XMLFilterImpl filter = new ImageFilter();
        filter.setContentHandler(handler);
        parser.parse(is, filter, metadata, context);

        /*
        ContentHandlerFactory factory = new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(factory); 
        RecursiveParserWrapper recursiveParser = new RecursiveParserWrapper(parser, true);
        context.set(Parser.class, parser);
        recursiveParser.parse(is, handler, metadata, context);
        //return handler.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < handler.getMetadataList().size(); i++) {
            String embeddedText = handler.getMetadataList().get(i).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
            if (embeddedText != null) {
                sb.append(embeddedText);
            }
        }
        return sb.toString();*/
    }

    //The Tika OOXML parser creates a img tag with a src value prefixed with "embedded" and there is no way to access the embedded
    // image at that point. As a workaround, the extractor and XML filter code below up adjusts the embedded img elements (targets), inserts 
    // the embedded images (source) as inline bas364 data, and then inserts a custom script that updates the img targets with the inlined sources. Custom CSS is also
    //inserted for additional stylization. If inline image OOXML support is unimportant this elaborate XML manipulation can be omitted.
    public static class AttachmentEmbeddedDocumentExtractor extends ParsingEmbeddedDocumentExtractor {

        public AttachmentEmbeddedDocumentExtractor(ParseContext context) {
            super(context);
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return true;
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws SAXException, IOException {

            if (TikaInputStream.isTikaInputStream(stream)) {
                TikaInputStream tikaStream = TikaInputStream.cast(stream);
                Object container = tikaStream.getOpenContainer();
                if (tikaStream.getLength() > 0) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    stream.transferTo(bos);
                    TikaInputStream newStream = TikaInputStream.get(bos.toByteArray());
                    newStream.setOpenContainer(container);

                    String attachmentName = metadata.get(Metadata.RESOURCE_NAME_KEY);
                    addAttachment(attachmentName, bos.toByteArray(), handler);
                    super.parseEmbedded(newStream, handler, metadata, outputHtml);
                }
            }
        }

        private void addAttachment(String attachmentName, byte[] bos, ContentHandler handler) throws IOException, SAXException {
            Metadata detectorMetadata = new Metadata();
            detectorMetadata.add(Metadata.RESOURCE_NAME_KEY, attachmentName);
            DefaultDetector detector = new DefaultDetector();
            org.apache.tika.mime.MediaType mediaType = detector.detect(TikaInputStream.get(bos), detectorMetadata);

            AttributesImpl attrs = new AttributesImpl();
            //attrs.addAttribute(XHTMLContentHandler.XHTML, "href", "href", , href);
            StringBuilder src = new StringBuilder("data:").append(mediaType.toString()).append(";base64, ");
            src.append(Base64.getEncoder().encodeToString(bos));
            if ("image".equals(mediaType.getType())) {
                attrs.addAttribute(XHTMLContentHandler.XHTML, "src", "src", "CDATA", src.toString());
                attrs.addAttribute(XHTMLContentHandler.XHTML, "data-src", "data-src", "CDATA", attachmentName);
                handler.startElement(XHTMLContentHandler.XHTML, "img", "img", attrs);
                handler.endElement(XHTMLContentHandler.XHTML, "img", "img");
            } else {
                attrs.addAttribute(XHTMLContentHandler.XHTML, "href", "", "", src.toString());
                attrs.addAttribute(XHTMLContentHandler.XHTML, "download", "", "", attachmentName);
                handler.startElement(XHTMLContentHandler.XHTML, "a", "a", attrs);
                handler.characters(attachmentName.toCharArray(), 0, attachmentName.length());
                handler.endElement(XHTMLContentHandler.XHTML, "a", "a");
            }
            handler.startElement(XHTMLContentHandler.XHTML, "br", "br", new AttributesImpl());
            handler.endElement(XHTMLContentHandler.XHTML, "br", "br");
        }
    }

    private static class ImageFilter extends XMLFilterImpl {
        public void startElement(String namespaceURI, String localName, String name, Attributes attributes) throws SAXException {
            if (localName.equals("img")) {
                String src = attributes.getValue("src");
                if (src != null && src.startsWith("embedded:")) {
                    AttributesImpl attrs = new AttributesImpl(attributes);
                    attrs.removeAttribute(attrs.getIndex("src"));
                    attrs.addAttribute(namespaceURI, "data-target", "data-target", "CDATA", src);
                    attrs.addAttribute(namespaceURI, "class", "class", "CDATA", "embedded-image-hide");
                    super.startElement(namespaceURI, localName, name, attrs);
                } else {
                    super.startElement(namespaceURI, localName, name, attributes);
                }
            } else if (localName.equals("head")) {
                super.startElement(namespaceURI, localName, name, attributes);
                super.startElement(namespaceURI, "style", "style", new AttributesImpl());
                String styleContent = ".embedded-image-hide, .package-entry h1 {\n"
                        + "  display: none;\n"
                        + "}\n"
                        + ".embedded-image {\n"
                        + "  width: 640px;\n"
                        + "  height: auto;\n"
                        + "}\n"
                        + ".attachment-entry h1 {\n"
                        + "  color: blue;\n"
                        + "  font-size: 20pt;\n"
                        + "}\n"
                        + "p {\n"
                        + "  white-space: pre;\n"
                        + "}\n";
                super.characters(styleContent.toCharArray(), 0, styleContent.length());
                super.endElement(namespaceURI, "style", "style");

                super.startElement(namespaceURI, "script", "script", new AttributesImpl());
                String scriptContent = "window.addEventListener('load', (event) = function() {\n"
                        + "    let imgTargets = document.querySelectorAll('img[data-target]');\n"
                        + "    for (let imgTarget of imgTargets){\n"
                        + "      let targetId = imgTarget.getAttribute('data-target').substring(9);\n"
                        + "      let rootPackage = imgTarget.closest('.package-entry');\n"
                        + "      if(rootPackage){\n"
                        + "        let imgSrc = rootPackage.querySelector(`img[data-src=\"${targetId}\"]`);\n"
                        + "        if(imgSrc){\n"
                        + "          imgTarget.setAttribute('src', imgSrc.getAttribute('src'));"
                        + "          imgTarget.classList.remove('embedded-image-hide');"
                        + "          imgTarget.classList.add('embedded-image');"
                        + "          imgTarget.parentNode.insertBefore(document.createElement(\"br\"), imgTarget.nextSibling);"
                        + "          imgSrc.parentNode.removeChild(imgSrc.nextSibling);"
                        + "          imgSrc.parentNode.removeChild(imgSrc);"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "});";
                super.characters(scriptContent.toCharArray(), 0, scriptContent.length());
                super.endElement(namespaceURI, "script", "script");
            } else {
                super.startElement(namespaceURI, localName, name, attributes);
            }

        }

    }

    @POST
    @Path("/file{contentPath:.*}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response fileUpload(@PathParam("contentPath") String contentPath, @MultipartForm MultipartFormDataInput input, @DefaultValue("attachment") @QueryParam("type") String fileType) {
        try {

            String path = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(path);
            Node node = session.getNode(path);
            for (InputPart part : input.getParts()) {
                String fileName = getFileName(part.getHeaders());
                String mimeType = String.format("%s/%s", part.getMediaType().getType(), part.getMediaType().getSubtype());
                if (fileName != null) {
                    Node fileNode = null;
                    Node resNode = null;
                    if (node.hasNode(fileName)) {
                        fileNode = node.getNode(fileName);
                        resNode = fileNode.getNode(JcrConstants.JCR_CONTENT);
                        String currentFileType = fileNode.hasProperty("qo:fileType") ? fileNode.getProperty("qo:fileType").getString() : "";
                        if (!fileType.equals(currentFileType)) {
                            String errMessage = String.format("File %s/%s exists but the file types mismatch, %s - %s,  unable to update", contentPath, fileName, currentFileType, fileType);
                            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Json.createObjectBuilder().add("status", "error").add("message", errMessage).build()).build();
                        }
                    } else {
                        fileNode = node.addNode(fileName, JcrConstants.NT_FILE);
                        String nodeType = "text/x-web-markdown".equals(mimeType) ? "qo:resource" : JcrConstants.NT_RESOURCE;
                        resNode = fileNode.addNode(JcrConstants.JCR_CONTENT, nodeType);
                        UserManager userManager = ((JackrabbitSession) session).getUserManager();
                        Group general = userManager.getAuthorizable("qoakus-general", Group.class);
                        AccessControlUtils.addAccessControlEntry(session, fileNode.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);
                    }
                    fileNode.addMixin("qo:file");
                    fileNode.setProperty("qo:fileType", fileType);
                    resNode.setProperty(JcrConstants.JCR_MIMETYPE, mimeType);
                    InputStream is = part.getBody(InputStream.class, null);
                    Binary contentValue = session.getValueFactory().createBinary(is);
                    resNode.setProperty(JcrConstants.JCR_DATA, contentValue);
                    Calendar lastModified = Calendar.getInstance();
                    resNode.setProperty(JcrConstants.JCR_LASTMODIFIED, lastModified);
                }
            }
            session.save();
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok").build()).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @POST
    @Path("/file/marked{contentPath:.*}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response fileMarkedUpload(@PathParam("contentPath") String contentPath, @MultipartForm MultipartFormDataInput input) {
        try {
            String path = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(path);
            Node node = session.getNode(path);
            InputPart part = input.getParts().get(0);

            String fileName = getFileName(part.getHeaders());
            if (fileName == null || fileName.startsWith("image.")) {
                int fileCount = 0;
                do {
                    fileName = String.format("image_%d.%s", ++fileCount, part.getMediaType().getSubtype());
                } while (node.hasNode(fileName));
            }

            Node fileNode = node.addNode(fileName, JcrConstants.NT_FILE);
            Node resNode = fileNode.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);

            fileNode.addMixin("qo:file");
            fileNode.setProperty("qo:fileType", "image");
            resNode.setProperty(JcrConstants.JCR_MIMETYPE, String.format("%s/%s", part.getMediaType().getType(), part.getMediaType().getSubtype()));
            InputStream is = part.getBody(InputStream.class, null);
            Binary contentValue = session.getValueFactory().createBinary(is);
            resNode.setProperty(JcrConstants.JCR_DATA, contentValue);
            Calendar lastModified = Calendar.getInstance();
            //lastModified.setTimeInMillis(file.lastModified());
            resNode.setProperty(JcrConstants.JCR_LASTMODIFIED, lastModified);

            session.save();
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok").add("fileName", fileName).build()).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @DELETE
    @Path("/file{contentPath:.*}/{fileName}")
    public Response fileDelete(@PathParam("contentPath") String contentPath, @PathParam("fileName") String fileName) {
        try {
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Session session = getSession(contentPath);
            Node node = session.getNode(contentPath);
            Node fileNode = node.getNode(fileName);
            fileNode.remove();
            session.save();
            return Response.status(Response.Status.OK).entity(Json.createObjectBuilder().add("status", "ok")).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }

    }

    private String getFileName(MultivaluedMap<String, String> header) {
        String[] contentDisposition = header.getFirst("Content-Disposition").split(";");

        for (String filename : contentDisposition) {
            if ((filename.trim().startsWith("filename"))) {

                String[] name = filename.split("=");

                String finalFileName = name[1].trim().replaceAll("\"", "");
                return finalFileName;
            }
        }
        return null;
    }

}
