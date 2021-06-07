package com.github.aaronanderson.qoakus.oidc;

import java.util.HashMap;
import java.util.Map;

import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityException;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityRef;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalUser;
import org.eclipse.microprofile.jwt.JsonWebToken;

public class OIDCUser implements ExternalUser {

    private final JsonWebToken idToken;
    private final ExternalIdentityRef ref;

    public OIDCUser(JsonWebToken idToken, ExternalIdentityRef ref) {
        this.idToken = idToken;
        this.ref = ref;
    }

    @Override
    public ExternalIdentityRef getExternalId() {
        return ref;
    }

    //Oak local userID. 
    //Okta preferred name is in UPN format, i.e. id@domain. Other OIDC IDPs may need to use a different claim name.
    //To diagnose print out the raw OIDC JWT token with idToken.getRawToken() and then copy and paste that value at https://jwt.io/ to see what claims are available.
    @Override
    public String getId() {
        return idToken.getClaim("preferred_username");
    }

    @Override
    public String getPrincipalName() {
        return idToken.getSubject();
    }

    //not needed, but could use domain from the ID UPN above to organize users.
    @Override
    public String getIntermediatePath() {
        return null;
    }

    @Override
    public Iterable<ExternalIdentityRef> getDeclaredGroups() throws ExternalIdentityException {
        return null;
    }

    @Override
    public Map<String, ?> getProperties() {
        Map<String, String> claims = new HashMap<>();
        for (String claimName : idToken.getClaimNames()) {
            Object claim = idToken.getClaim(claimName);
            if (claim instanceof String) {
                claims.put(claimName, (String) claim);
            }
        }
        return claims;
    }

}
