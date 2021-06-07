package com.github.aaronanderson.qoakus.oidc;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.enterprise.context.ApplicationScoped;
import javax.jcr.Credentials;
import javax.security.auth.login.LoginException;

import org.apache.jackrabbit.oak.spi.security.authentication.credentials.CredentialsSupport;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalGroup;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentity;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityException;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityProvider;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityRef;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalUser;
import org.apache.jackrabbit.oak.spi.security.authentication.external.PrincipalNameResolver;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

//External IDP as documented here: http://jackrabbit.apache.org/oak/docs/security/authentication/externalloginmodule.html
//oak-auth-ldap provides an external IDP reference example.
@ApplicationScoped
public class OIDCIdentityProvider implements ExternalIdentityProvider, PrincipalNameResolver, CredentialsSupport {

    @ConfigProperty(name = "qoakus.oidc.provider-name", defaultValue = "oidc")
    String providerName;

    @Override
    public String fromExternalIdentityRef(ExternalIdentityRef externalIdentityRef) throws ExternalIdentityException {
        if (!isMyRef(externalIdentityRef)) {
            throw new ExternalIdentityException("Foreign IDP " + externalIdentityRef.getString());
        }
        return externalIdentityRef.getId();
    }

    @Override
    public String getName() {
        return providerName;
    }

    @Override
    public ExternalIdentity getIdentity(ExternalIdentityRef ref) throws ExternalIdentityException {
        if (!isMyRef(ref)) {
            return null;
        }
        String id = ref.getId();
        return null;
    }

    @Override
    public ExternalUser getUser(String userId) throws ExternalIdentityException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ExternalUser authenticate(Credentials credentials) throws ExternalIdentityException, LoginException {
        OIDCTokenCredentials oidcCredentials = (OIDCTokenCredentials) credentials;
        OIDCUser user = new OIDCUser(oidcCredentials.getIdToken(), new ExternalIdentityRef(oidcCredentials.getIdToken().getSubject(), this.getName()));
        //setAttributes(oidcCredentials, user.getProperties());
        return user;
    }

    @Override
    public ExternalGroup getGroup(String name) throws ExternalIdentityException {
        return null;
    }

    @Override
    public Iterator<ExternalUser> listUsers() throws ExternalIdentityException {
        return null;
    }

    @Override
    public Iterator<ExternalGroup> listGroups() throws ExternalIdentityException {
        return null;
    }

    private boolean isMyRef(ExternalIdentityRef ref) {
        final String refProviderName = ref.getProviderName();
        return refProviderName == null || refProviderName.isEmpty() || getName().equals(refProviderName);
    }

    @Override
    public Set<Class> getCredentialClasses() {
        return Set.of(OIDCTokenCredentials.class);
    }

    @Override
    public String getUserId(Credentials credentials) {
        OIDCTokenCredentials oidcCredentials = (OIDCTokenCredentials) credentials;
        return oidcCredentials.getIdToken().getSubject();
    }

    @Override
    public Map<String, ?> getAttributes(Credentials credentials) {
        if (credentials instanceof OIDCTokenCredentials) {
            final OIDCTokenCredentials sc = (OIDCTokenCredentials) credentials;
            return Collections.unmodifiableMap(sc.getAttributes());
        } else {
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean setAttributes(Credentials credentials, Map<String, ?> attributes) {
        if (credentials instanceof OIDCTokenCredentials) {
            OIDCTokenCredentials sc = (OIDCTokenCredentials) credentials;
            sc.getAttributes().putAll(attributes);
            return true;
        } else {
            return false;
        }
    }

}
