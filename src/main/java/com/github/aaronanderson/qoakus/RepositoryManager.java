package com.github.aaronanderson.qoakus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.security.Privilege;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.security.authentication.user.LoginModuleImpl;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.spi.security.ConfigurationParameters;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityProvider;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityProviderManager;
import org.apache.jackrabbit.oak.spi.security.authentication.external.SyncHandler;
import org.apache.jackrabbit.oak.spi.security.authentication.external.SyncManager;
import org.apache.jackrabbit.oak.spi.security.authentication.external.basic.DefaultSyncConfig;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.DefaultSyncConfigImpl;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.DefaultSyncHandler;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.ExternalIDPManagerImpl;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.ExternalLoginModule;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.SyncManagerImpl;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

import com.github.aaronanderson.qoakus.oidc.OIDCIdentityProvider;

/*import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.aws.AwsContext;
import org.apache.jackrabbit.oak.segment.aws.AwsPersistence;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeStore;

import com.google.common.io.Files;
*/
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class RepositoryManager {

    static Logger logger = Logger.getLogger(RepositoryManager.class);

    @Inject
    OIDCIdentityProvider oidcIdp;

    private Repository repository;

    private FileStore fileStore;

    void onStart(@Observes StartupEvent ev) {

        try {
            //consider direct binary download support
            //https://jackrabbit.apache.org/oak/docs/features/direct-binary-access.html
            //Node ntFile = session.getNode("/content/file.png");
            //Node ntResource = ntFile.getNode("jcr:content");
            //Binary binary = ntResource.getProperty("jcr:data").getBinary();

            /*AwsContext awsContext;// = AwsContext.create(s3, bucketName, AWS_ROOT_PATH, ddb, AWS_JOURNAL_TABLE_NAME,                AWS_LOCK_TABLE_NAME);
            AwsPersistence persistence = new AwsPersistence(awsContext);
            FileStore fileStore = FileStoreBuilder.fileStoreBuilder(Files.createTempDir()).withCustomPersistence(persistence).build();
            NodeStore nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();
            Oak oak = new Oak(nodeStore);*/

            //setup ExteralLoginModule
            //http://jackrabbit.apache.org/oak/docs/security/authentication.html
            Configuration c = new Configuration() {
                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String applicationName) {
                    Map<String, String> options = new HashedMap<>();
                    options.put(ExternalLoginModule.PARAM_SYNC_HANDLER_NAME, "default");
                    options.put(ExternalLoginModule.PARAM_IDP_NAME, "oidc");
                    AppConfigurationEntry customEntry = new AppConfigurationEntry(ExternalLoginModule.class.getName(), LoginModuleControlFlag.SUFFICIENT, options);
                    AppConfigurationEntry defaultEntry = new AppConfigurationEntry(LoginModuleImpl.class.getName(), LoginModuleControlFlag.REQUIRED, Collections.emptyMap());
                    return new AppConfigurationEntry[] { customEntry, defaultEntry };
                }
            };
            Configuration.setConfiguration(c);

            Oak oak = new Oak();
            oak.with("qoakus");

            Whiteboard whiteboard = oak.getWhiteboard();
            whiteboard.register(ExternalIdentityProviderManager.class, new ExternalIDPManagerImpl(whiteboard), Collections.emptyMap());
            whiteboard.register(SyncManager.class, new SyncManagerImpl(whiteboard), Collections.emptyMap());
            whiteboard.register(ExternalIdentityProvider.class, oidcIdp, Collections.emptyMap());

            HashMap<String, Object> params = new HashMap<>();
            params.put(DefaultSyncConfigImpl.PARAM_USER_PATH_PREFIX, "oidc");
            params.put(DefaultSyncConfigImpl.PARAM_USER_PROPERTY_MAPPING, List.of("rep:fullname=name", "profile/email=email", "profile/name=name"));
            params.put(DefaultSyncConfigImpl.PARAM_USER_AUTO_MEMBERSHIP, List.of("qoakus-general"));
            DefaultSyncConfig syncConfig = DefaultSyncConfigImpl.of(ConfigurationParameters.of(params));

            whiteboard.register(SyncHandler.class, new DefaultSyncHandler(syncConfig), Collections.emptyMap());

            //OIDC/OAuth does not provide a user/group list feature. Identity details are only available at the user authentication token level. 
            //If external user/group management is needed for a particular IDP, for example Okta, extend the OIDCIdentityProvider to return user and group list data and then 
            //register an ExternalPrincipalConfiguration configuration per http://jackrabbit.apache.org/oak/docs/security/authentication/external/dynamic.html Below is an example configuration:
            //https://github.com/apache/jackrabbit-oak/blob/acdfc74012ebed033a34a78727579508919b1818/oak-benchmarks/src/main/java/org/apache/jackrabbit/oak/benchmark/authentication/external/AbstractExternalTest.java#L238
            //https://github.com/apache/jackrabbit-oak/blob/acdfc74012ebed033a34a78727579508919b1818/oak-benchmarks/src/main/java/org/apache/jackrabbit/oak/benchmark/authentication/external/AbstractExternalTest.java#L192

            //https://forums.aws.amazon.com/thread.jspa?threadID=285934
            /*ElasticConnection coordinate = ElasticConnection.newBuilder().withIndexPrefix("/").withConnectionParameters("https", "", 9000).withApiKeys(null, null);
            ElasticIndexEditorProvider editorProvider = new ElasticIndexEditorProvider(coordinate, new ExtractedTextCache(10 * FileUtils.ONE_MB, 100));
            ElasticIndexProvider indexProvider = new ElasticIndexProvider(coordinate, new ElasticMetricHandler(StatisticsProvider.NOOP));
            oak.with(editorProvider)
                .with((Observer) indexProvider)
                .with((QueryIndexProvider) indexProvider)
                .with(new PropertyIndexEditorProvider())
                .with(new NodeTypeIndexProvider())*/

            repository = new Jcr(oak).createRepository();

            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            //TODO the internal admin session should never be externally accessible outside the JVM but consider setting a different default userID/password combo, UserConstants.PARAM_ADMIN_ID, or omitting the password
            //UserConstants.PARAM_OMIT_ADMIN_PW and use the JVM Subject.doAS like https://github.com/apache/jackrabbit-oak/blob/acdfc74012ebed033a34a78727579508919b1818/oak-core/src/test/java/org/apache/jackrabbit/oak/security/user/UserInitializerTest.java#L176

            NodeTypeManager mgr = session.getWorkspace().getNodeTypeManager();

            if (!mgr.hasNodeType("qo:ContentType")) {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("cnd/content.cnd");
                CndImporter.registerNodeTypes(new InputStreamReader(is), session);

                //Manual NodeType creation:
                /*NodeTypeTemplate type = mgr.createNodeTypeTemplate();
                type.setName("ns:NodeType");
                type.setDeclaredSuperTypeNames(new String[] { "ns:ParentType1", "ns:ParentType2" });
                type.setAbstract(true);
                type.setOrderableChildNodes(true);
                type.setMixin(true);
                type.setQueryable(true);
                type.setPrimaryItemName("ex:property");*/
                //https://docs.adobe.com/content/docs/en/spec/jcr/2.0/index.html
                //https://github.com/nabils/jackrabbit/blob/master/jackrabbit-core/src/main/resources/org/apache/jackrabbit/core/nodetype/builtin_nodetypes.cnd

                //http://jackrabbit.apache.org/oak/docs/security/accesscontrol/editing.html
                //PrincipalManager principalManager = ((JackrabbitSession) session).getPrincipalManager();
                UserManager userManager = ((JackrabbitSession) session).getUserManager();
                Group general = userManager.createGroup("qoakus-general");
                User testUser = userManager.createUser("test", "test");
                general.addMember(testUser);
                Node userNode = session.getNode(testUser.getPath());
                Node profileNode =userNode.addNode("profile", "nt:unstructured");
                profileNode.setProperty("name", "Test User");
                profileNode.setProperty("email", "test@test");
                /*Alternative access control management:
                AccessControlManager acMgr = session.getAccessControlManager();
                JackrabbitAccessControlList acl = AccessControlUtils.getAccessControlList(session, root.getPath());
                acl.addEntry(general.getPrincipal(), AccessControlUtils.privilegesFromNames(session, Privilege.JCR_READ), true);
                acMgr.setPolicy(session.getRootNode().getPath(), acl);
                AccessControlPolicy[] policies = acMgr.getPolicies("/");
                for (AccessControlPolicy policy : policies) {
                    System.out.format("Policy %s\n", policy);
                }*/

                Node root = session.getRootNode();
                root.addMixin("qo:content");
                root.setProperty("qo:title", "Main");
                addFile(root, "md/main.md", "content.md", "text/markdown", "main", session);
                AccessControlUtils.addAccessControlEntry(session, root.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_READ }, true);

                //String id =UUID.randomUUID().toString();
                Node category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "Java");
                addFile(category, "md/java.md", "content.md", "text/markdown", "main", session);
                AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

                category = category.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "Quarkus");
                addFile(category, "md/quarkus.md", "content.md", "text/markdown", "main", session);
                addFile(category, "md/quarkus.pdf", "quarkus.pdf", "application/pdf", "attachment", session);
                addFile(category, "md/quarkus.jpg", "quarkus.jpg", "image/jpg", "image", session);
                AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

                category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "JavaScript");
                addFile(category, "md/javascript.md", "content.md", "text/markdown", "main", session);
                AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

                category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "CSS");
                addFile(category, "md/css.md", "content.md", "text/markdown", "main", session);
                AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

                //Will not be visible because the ACL denies it
                category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "Protected");
                addFile(category, "md/css.md", "protected.md", "text/markdown", "main", session);
                AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, false);

                //AccessControlUtils.grantAllToEveryone(session, "/");
                //AccessControlUtils.denyAllToEveryone(session, "/");

                //saveRepositoryXML(repository);
                session.save();

            }

        } catch (Throwable t) {
            logger.error("Oak repository error", t);
        }
    }

    static void saveRepositoryXML(Repository repository) throws RepositoryException {
        try {
            Session adminSession = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            final TransformerFactory tf = TransformerFactory.newInstance();
            final TransformerHandler th = ((SAXTransformerFactory) tf).newTransformerHandler();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final DOMResult result = new DOMResult();
            th.setResult(result);

            adminSession.exportSystemView("/", th, true, false);
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            Path logFile = Paths.get("/tmp/repo.xml");
            transformer.transform(new DOMSource(result.getNode()), new StreamResult(Files.newOutputStream(logFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)));
            logger.infof("Repository XML exported to %s", logFile.toAbsolutePath());
        } catch (TransformerException | SAXException | IOException e) {
            throw new RepositoryException(e);
        }

    }

    private void addFile(Node folderNode, String resourcePath, String fileName, String mimeType, String fileType, Session session) throws RepositoryException {
        Node fileNode = folderNode.addNode(fileName, JcrConstants.NT_FILE);
        fileNode.addMixin("qo:fileType");
        fileNode.setProperty("qo:fileType", fileType);
        //create the mandatory child node - jcr:content
        Node resNode = fileNode.addNode(JcrConstants.JCR_CONTENT, JcrConstants.NT_RESOURCE);
        resNode.setProperty(JcrConstants.JCR_MIMETYPE, mimeType);
        //resNode.setProperty ("jcr:encoding", encoding);
        //ByteArrayInputStream bis = new ByteArrayInputStream("Contents".getBytes());
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        Binary contentValue = session.getValueFactory().createBinary(is);
        resNode.setProperty(JcrConstants.JCR_DATA, contentValue);
        Calendar lastModified = Calendar.getInstance();
        //lastModified.setTimeInMillis(file.lastModified());
        resNode.setProperty(JcrConstants.JCR_LASTMODIFIED, lastModified);
    }

    @Produces
    public Repository repository() {
        return repository;
    }

    void onShutdown(@Observes ShutdownEvent ev) {
        //fileStore.close();

    }

}


