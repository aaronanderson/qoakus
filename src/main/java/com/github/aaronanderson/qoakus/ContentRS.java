package com.github.aaronanderson.qoakus;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.TimeZone;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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

import org.apache.jackrabbit.JcrConstants;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.xml.sax.SAXException;

import io.quarkus.tika.TikaParser;

@Path("/content")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContentRS {

    static Logger logger = Logger.getLogger(ContentRS.class);

    @Inject
    Repository repository;

    //Don't use the Quarkus custom parser, use the standard one for more control.
    @Inject
    TikaParser parser;

    /*
    @GET
    @Path("main")
    public Response main() {
        try {
            JsonObjectBuilder result = Json.createObjectBuilder();
            JsonArrayBuilder main = Json.createArrayBuilder();
    
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            Node root = session.getRootNode();
            NodeIterator nodeIterator = root.getNodes();
            while (nodeIterator.hasNext()) {
                Node node = nodeIterator.nextNode();
                if (node.isNodeType("qo:ContentType")) {
                    JsonObjectBuilder content = Json.createObjectBuilder();
                    content.add("path", node.getPath());
                    content.add("title", node.getProperty("qo:title").getString());
                    main.add(content);
                }
            }
            result.add("main", main);
            result.add("status", "ok");
            return Response.status(200).entity(result.build()).build();
    
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }*/

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

            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
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
                    if (child.isNodeType("qo:fileType")) {
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
    @Path("{contentPath:.*}")
    public Response newContent(@PathParam("contentPath") String contentPath) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

    }

    @PUT
    @Path("{contentPath:.*}")
    public Response editContent(@PathParam("contentPath") String contentPath) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();

    }

    @DELETE
    @Path("{contentPath:.*}")
    public Response deleteContent(@PathParam("contentPath") String contentPath) {
        try {
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
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

            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;

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
                response.entity(binary.getStream());
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

            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;

            Node node = session.getNode(contentPath);

            if (node.hasNode(fileName)) {
                Node ntFile = node.getNode(fileName);
                Node ntResource = ntFile.getNode(JcrConstants.JCR_CONTENT);
                String mimeType = ntResource.getProperty(JcrConstants.JCR_MIMETYPE).getString();
                Binary binary = ntResource.getProperty(JcrConstants.JCR_DATA).getBinary();

                ResponseBuilder response = Response.status(Response.Status.OK);                
                //ContentHandlerFactory factory = new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1);
                //RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(factory);
                ToHTMLContentHandler handler = new ToHTMLContentHandler();
                String text = parser.getText(binary.getStream(), handler);
                //String text = parseFile(binary.getStream());
                response.entity(text);
                response.type("text/html");
                return response.build();
            }
            return Response.status(Response.Status.NOT_FOUND).entity(String.format("Content %s file %s unavailable", contentPath, fileName)).build();

        } catch (Exception e) {
            logger.error("", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(String.format("Content %s file %s retrieval error: %s", contentPath, fileName, e.getMessage())).build();
        }
    }

    //not used but preserved for future reference of potentially inlining images into the HTML
    private String parseFile(InputStream is) throws IOException, SAXException, TikaException {
        ParseContext context = new ParseContext();

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        pdfConfig.setExtractUniqueInlineImagesOnly(true);
        context.set(PDFParserConfig.class, pdfConfig);

        /*
        EmbeddedDocumentExtractor embeddedDocumentExtractor = new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }
        
            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                    throws SAXException, IOException {
                java.nio.file.Path outputDir = Files.createTempDirectory("tika");
                Files.createDirectories(outputDir);
        
                java.nio.file.Path outputPath = new File(outputDir.toString() + "/" + metadata.get(Metadata.RESOURCE_NAME_KEY)).toPath();
                Files.deleteIfExists(outputPath);
                Files.copy(stream, outputPath);
                System.out.format("Copied embedded %s\n", outputPath.toAbsolutePath());
            }
        };
        
        context.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);*/

        //ToHTMLContentHandler handler = new ToHTMLContentHandler();
        ContentHandlerFactory factory = new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(factory);
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        RecursiveParserWrapper recursiveParser = new RecursiveParserWrapper(parser, true);
        context.set(Parser.class, parser);
        recursiveParser.parse(is, handler, metadata, context);
        //return handler.toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < handler.getMetadataList().size(); i++) {
            String embeddedText = handler.getMetadataList().get(i).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT);
            // the embedded text can be null if the given document is an image
            // and no text recognition parser is enabled
            if (embeddedText != null) {
                sb.append(embeddedText);
            }
        }
        return sb.toString();
    }

    @POST
    @Path("/file{contentPath:.*}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response fileUpload(@PathParam("contentPath") String contentPath, @MultipartForm MultipartFormDataInput input, @DefaultValue("attachment") @QueryParam("type") String fileType) {
        try {
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            String path = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
            Node node = session.getNode(path);
            for (InputPart part : input.getParts()) {
                String fileName = getFileName(part.getHeaders());
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
                        resNode = fileNode.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
                    }
                    fileNode.addMixin("qo:fileType");
                    fileNode.setProperty("qo:fileType", fileType);
                    resNode.setProperty(JcrConstants.JCR_MIMETYPE, String.format("%s/%s", part.getMediaType().getType(), part.getMediaType().getSubtype()));
                    InputStream is = part.getBody(InputStream.class, null);
                    Binary contentValue = session.getValueFactory().createBinary(is);
                    resNode.setProperty(JcrConstants.JCR_DATA, contentValue);
                    Calendar lastModified = Calendar.getInstance();
                    //lastModified.setTimeInMillis(file.lastModified());
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
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            String path = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
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

            fileNode.addMixin("qo:fileType");
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
        } catch (

        Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

    @DELETE
    @Path("/file{contentPath:.*}/{fileName}")
    public Response fileDelete(@PathParam("contentPath") String contentPath, @PathParam("fileName") String fileName) {
        try {
            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
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
