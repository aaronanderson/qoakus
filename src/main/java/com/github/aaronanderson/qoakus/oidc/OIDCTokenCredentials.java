package com.github.aaronanderson.qoakus.oidc;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Credentials;

import org.eclipse.microprofile.jwt.JsonWebToken;

public class OIDCTokenCredentials implements Credentials {

    private final JsonWebToken idToken;
    private final Map<String, Object> attributes;

    public OIDCTokenCredentials(JsonWebToken idToken) {
        this.idToken = idToken;
        this.attributes = new HashMap<>();
    }

    public JsonWebToken getIdToken() {
        return idToken;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

}
