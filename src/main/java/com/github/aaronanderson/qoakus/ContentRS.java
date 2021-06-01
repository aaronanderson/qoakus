package com.github.aaronanderson.qoakus;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
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
    public Response scripts(@PathParam("contentPath") String contentPath) {
        try {
            JsonObjectBuilder result = Json.createObjectBuilder();
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
                if (child.isNodeType("qo:ContentType")) {
                    JsonObjectBuilder content = Json.createObjectBuilder();
                    content.add("path", child.getPath());
                    content.add("title", child.getProperty("qo:title").getString());
                    children.add(content);
                }
            }
            JsonArray childNodes = children.build();
            if (childNodes.size() > 0) {
                result.add("children", childNodes);
            }
            result.add("contentPath", contentPath);
            result.add("status", "ok");
            return Response.status(200).entity(result.build()).build();

        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

}
