package com.github.aaronanderson.qoakus;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import io.quarkus.oidc.IdToken;

@Path("/user")
@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserRS {

    static Logger logger = Logger.getLogger(UserRS.class);

    @Inject
    Repository repository;

    @Inject
    @IdToken
    Instance<JsonWebToken> idToken;

    @GET
    @Path("/")
    public Response viewContent(@PathParam("contentPath") String contentPath) {
        try {
            JsonObjectBuilder result = Json.createObjectBuilder();
            Session session = ContentRS.getSession(idToken, repository);
            String userId = session.getUserID();
            UserManager userManager = ((JackrabbitSession) session).getUserManager();
            User user = userManager.getAuthorizable(userId, User.class);
            String principal = user.getPrincipal().getName();
            String userPath = user.getPath();
            Node userNode = session.getNode(userPath);
            Node userProfile = userNode.getNode("profile");
            String name = userProfile.getProperty("name").getString();
            String email = userProfile.getProperty("email").getString();
            JsonObjectBuilder userJson = Json.createObjectBuilder();
            userJson.add("userId", userId);
            userJson.add("principal", principal);
            userJson.add("path", userPath);
            userJson.add("name", name);
            userJson.add("email", email);
            result.add("user", userJson);
            result.add("status", "ok");
            return Response.status(200).entity(result.build()).build();
        } catch (Exception e) {
            logger.error("", e);
            JsonObject status = Json.createObjectBuilder().add("status", "error").add("message", e.getMessage() != null ? e.getMessage() : "").build();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(status).build();
        }
    }

}
