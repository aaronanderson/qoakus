package com.github.aaronanderson.qoakus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.index.elastic.ElasticConnection;
import org.apache.jackrabbit.oak.plugins.index.elastic.ElasticMetricHandler;
import org.apache.jackrabbit.oak.plugins.index.elastic.index.ElasticIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.elastic.query.ElasticIndexProvider;
import org.apache.jackrabbit.oak.plugins.index.nodetype.NodeTypeIndexProvider;
import org.apache.jackrabbit.oak.plugins.index.property.PropertyIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.search.ExtractedTextCache;
import org.apache.jackrabbit.oak.security.authentication.user.LoginModuleImpl;
import org.apache.jackrabbit.oak.segment.SegmentCache;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.aws.AwsContext;
import org.apache.jackrabbit.oak.segment.aws.AwsPersistence;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.spi.commit.Observer;
import org.apache.jackrabbit.oak.spi.query.QueryIndexProvider;
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
import org.apache.jackrabbit.oak.spi.state.NodeStore;
import org.apache.jackrabbit.oak.spi.whiteboard.Whiteboard;
import org.apache.jackrabbit.oak.stats.StatisticsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.jboss.logging.Logger;
import org.xml.sax.SAXException;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.github.aaronanderson.qoakus.oidc.OIDCIdentityProvider;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class RepositoryManager {

    static Logger logger = Logger.getLogger(RepositoryManager.class);

    @Inject
    OIDCIdentityProvider oidcIdp;

    @ConfigProperty(name = "qoakus.aws.region")
    Optional<String> region;

    @ConfigProperty(name = "qoakus.aws.s3-bucket")
    Optional<String> bucketName;

    //required, empty string not supported and results in invalid S3 paths like s3:bucket//log...
    @ConfigProperty(name = "qoakus.aws.s3-root-dir")
    Optional<String> rootDirectory;

    @ConfigProperty(name = "qoakus.aws.dynamodb-journal-table-name")
    Optional<String> journalTableName;

    @ConfigProperty(name = "qoakus.aws.dynamodb-lock-table-name")
    Optional<String> lockTableName;

    //nothings is actually stored in this directory due to the custom persistence.
    @ConfigProperty(name = "qoakus.aws.filestore-path")
    Optional<String> fileStorePath;

    @ConfigProperty(name = "qoakus.aws.es-host")
    Optional<String> esHost;

    private Repository repository;

    private FileStore fileStore;

    void onStart(@Observes StartupEvent ev) {

        try {
            Oak oak = null;
            if (region.isPresent() && bucketName.isPresent() && rootDirectory.isPresent() && journalTableName.isPresent() && lockTableName.isPresent()) {
                logger.infof("Configuring AWS persistence");
                AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(region.get()).build();
                AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard().withRegion(region.get()).build();
                AwsContext awsContext = AwsContext.create(s3Client, bucketName.get(), rootDirectory.get(), dynamoDBClient, journalTableName.get(), lockTableName.get());
                AwsPersistence awsPersistence = new AwsPersistence(awsContext);

                Path baseFileStoreDir = fileStorePath.isPresent() ? Paths.get(fileStorePath.get()) : Files.createTempDirectory("qouakus");
                Path fileStorePath = baseFileStoreDir.resolve("local");
                Files.createDirectories(fileStorePath);
                fileStore = FileStoreBuilder.fileStoreBuilder(fileStorePath.toFile())
                        .withCustomPersistence(awsPersistence)
                        .withMemoryMapping(false)
                        .withStrictVersionCheck(true)
                        .withSegmentCacheSize(SegmentCache.DEFAULT_SEGMENT_CACHE_MB).build();
                NodeStore nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();
                oak = new Oak(nodeStore);
            } else {
                oak = new Oak();
                oak.with("qoakus");
            }
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

            //Register the External Identity Provider
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

            if (esHost.isPresent()) {
                ElasticConnection coordinate = ElasticConnection.newBuilder().withIndexPrefix("/").withConnectionParameters("https", esHost.get(), 443).build();
                //override the standard elasticsearch client with one configured for AWS ES.  ElasticConnection has a private constructor/is final so use reflection to set the new client for now.
                //https://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/es-request-signing.html
                AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
                AWS4Signer signer = new AWS4Signer();
                signer.setServiceName("es");
                signer.setRegionName(region.get());
                HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor("es", signer, credentialsProvider);
                RestHighLevelClient awsClient = new RestHighLevelClient(RestClient.builder(new HttpHost(esHost.get(), 443, "https")).setHttpClientConfigCallback(hacb -> hacb.addInterceptorLast(interceptor)));
                Field clientField = ElasticConnection.class.getDeclaredField("client");
                clientField.setAccessible(true);
                clientField.set(coordinate, awsClient);

                ElasticIndexEditorProvider editorProvider = new ElasticIndexEditorProvider(coordinate, new ExtractedTextCache(10 * FileUtils.ONE_MB, 100));
                ElasticIndexProvider indexProvider = new ElasticIndexProvider(coordinate, new ElasticMetricHandler(StatisticsProvider.NOOP));
                oak.with(editorProvider)
                        .with((Observer) indexProvider)
                        .with((QueryIndexProvider) indexProvider)
                        .with(new PropertyIndexEditorProvider())
                        .with(new NodeTypeIndexProvider());
            }

            repository = new Jcr(oak).createRepository();

            Session session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));
            //TODO the internal admin session should never be externally accessible outside the JVM but consider setting a different default userID/password combo, UserConstants.PARAM_ADMIN_ID, or omitting the password
            //UserConstants.PARAM_OMIT_ADMIN_PW and use the JVM Subject.doAS like https://github.com/apache/jackrabbit-oak/blob/acdfc74012ebed033a34a78727579508919b1818/oak-core/src/test/java/org/apache/jackrabbit/oak/security/user/UserInitializerTest.java#L176

            //saveRepositoryXML(repository);
            Node root = session.getRootNode();
            if (!root.hasProperty("qo:title")) {
                logger.infof("Performing one-time initial configuration");
                NodeTypeManager nodeTypeManager = session.getWorkspace().getNodeTypeManager();
                if (!nodeTypeManager.hasNodeType("qo:ContentType")) {
                    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("cnd/content.cnd");
                    CndImporter.registerNodeTypes(new InputStreamReader(is), session);
                    session.save();
                }
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
                Node profileNode = userNode.addNode("profile", "nt:unstructured");
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

                root.addMixin("qo:content");
                root.setProperty("qo:title", "Main");
                addFile(root, "md/main.md", "content.md", "text/markdown", "main", session);
                AccessControlUtils.addAccessControlEntry(session, root.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_READ, Privilege.JCR_ADD_CHILD_NODES, Privilege.JCR_REMOVE_CHILD_NODES }, true);

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

    static void addFile(Node folderNode, String resourcePath, String fileName, String mimeType, String fileType, Session session) throws RepositoryException {
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
        //It is very important to cleanly shutdown the filestore/AWS persistence. Otherwise a lengthy recovery will occur on the next startup.
        if (fileStore != null) {
            fileStore.close();
        }

    }

}
