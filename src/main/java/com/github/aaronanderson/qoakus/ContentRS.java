package com.github.aaronanderson.qoakus;

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
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.jackrabbit.JcrConstants;
import org.jboss.logging.Logger;

@Path("/content")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ContentRS {

    static Logger logger = Logger.getLogger(ContentRS.class);

    @Inject
    Repository repository;

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

    @GET
    @Path("view{contentPath:.*}")
    public Response viewContent(@PathParam("contentPath") String contentPath) {
        try {
            JsonObjectBuilder result = Json.createObjectBuilder();
            JsonArrayBuilder files = Json.createArrayBuilder();
            JsonArrayBuilder children = Json.createArrayBuilder();

            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            contentPath = contentPath.startsWith("/") ? contentPath : "/" + contentPath;
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

    @GET
    @Path("file{contentPath:.*}/{fileName}")
    public Response file(@PathParam("contentPath") String contentPath, @PathParam("fileName") String fileName) {
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

}
