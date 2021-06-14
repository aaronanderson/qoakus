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
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.security.Privilege;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginException;
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
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.ContentRepository;
import org.apache.jackrabbit.oak.api.ContentSession;
import org.apache.jackrabbit.oak.api.Root;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.index.IndexConstants;
import org.apache.jackrabbit.oak.plugins.index.elastic.ElasticConnection;
import org.apache.jackrabbit.oak.plugins.index.elastic.ElasticMetricHandler;
import org.apache.jackrabbit.oak.plugins.index.elastic.index.ElasticIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.elastic.query.ElasticIndexProvider;
import org.apache.jackrabbit.oak.plugins.index.elastic.util.ElasticIndexDefinitionBuilder;
import org.apache.jackrabbit.oak.plugins.index.nodetype.NodeTypeIndexProvider;
import org.apache.jackrabbit.oak.plugins.index.property.PropertyIndexEditorProvider;
import org.apache.jackrabbit.oak.plugins.index.search.ExtractedTextCache;
import org.apache.jackrabbit.oak.plugins.index.search.FulltextIndexConstants;
import org.apache.jackrabbit.oak.plugins.index.search.util.IndexDefinitionBuilder;
import org.apache.jackrabbit.oak.plugins.index.search.util.IndexDefinitionBuilder.AggregateRule;
import org.apache.jackrabbit.oak.plugins.index.search.util.IndexDefinitionBuilder.IndexRule;
import org.apache.jackrabbit.oak.plugins.memory.MemoryNodeStore;
import org.apache.jackrabbit.oak.security.authentication.user.LoginModuleImpl;
import org.apache.jackrabbit.oak.segment.SegmentCache;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.aws.AwsContext;
import org.apache.jackrabbit.oak.segment.aws.AwsPersistence;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.segment.file.InvalidFileStoreVersionException;
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

    private Oak oak;
    private Repository repository;
    private ContentRepository contentRepository;
    private FileStore fileStore;

    void onStart(@Observes StartupEvent ev) {
        try {
            createOak();
            configureEIP();
            configureAWSElasticsearch();

            Jcr jcr = new Jcr(oak);
            repository = jcr.createRepository();
            contentRepository = jcr.createContentRepository();

            repositoryInit();

        } catch (Throwable t) {
            logger.error("Oak repository error", t);
        }
    }

    private Oak createOak() throws InvalidFileStoreVersionException, IOException {
        NodeStore nodeStore = null;
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
            nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();

        } else {
            nodeStore = new MemoryNodeStore();
        }

        oak = new Oak(nodeStore);
        oak.with("qoakus");
        return oak;
    }

    private void configureEIP() {
        //Setup an ExteralLoginModule
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

    }

    private void configureAWSElasticsearch() throws NoSuchFieldException, IllegalAccessException {
        if (esHost.isPresent()) {
            ElasticConnection coordinate = ElasticConnection.newBuilder().withIndexPrefix("oak-elastic").withConnectionParameters("https", esHost.get(), 443).build();
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
                    .with(new NodeTypeIndexProvider())
                    .withAsyncIndexing("async", 5);

            //If the indexes are subject to frequent changes enable a cleaner task to remove unused ones.
            //ElasticIndexCleaner task = new ElasticIndexCleaner(coordinate, nodeStore, 24 * 60 * 60);
            //WhiteboardUtils.scheduleWithFixedDelay(whiteboard, task, 60 * 60);
        }
    }

    private void repositoryInit() throws RepositoryException, IOException, ParseException, LoginException, CommitFailedException {
        //TODO the internal admin session should never be externally accessible outside the JVM but consider setting a different default userID/password combo, UserConstants.PARAM_ADMIN_ID, or omitting the password
        //UserConstants.PARAM_OMIT_ADMIN_PW and use the JVM Subject.doAS like https://github.com/apache/jackrabbit-oak/blob/acdfc74012ebed033a34a78727579508919b1818/oak-core/src/test/java/org/apache/jackrabbit/oak/security/user/UserInitializerTest.java#L176

        SimpleCredentials adminCredentials = new SimpleCredentials("admin", "admin".toCharArray());

        Session session = repository.login(adminCredentials);
        Node root = session.getRootNode();
        if (!root.hasProperty("qo:title")) {
            logger.infof("Performing one-time initial configuration");

            loadCND(session);

            createIndexes(adminCredentials);

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

            //AccessControlUtils.grantAllToEveryone(session, "/");
            //AccessControlUtils.denyAllToEveryone(session, "/");

            populateRepository(root, session, general);

            session.save();

            saveRepositoryXML(repository);

        }
    }

    private void loadCND(Session session) throws RepositoryException, ParseException, IOException {
        NodeTypeManager nodeTypeManager = session.getWorkspace().getNodeTypeManager();
        if (!nodeTypeManager.hasNodeType("qo:ContentType")) {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("cnd/content.cnd");
            CndImporter.registerNodeTypes(new InputStreamReader(is), session);
            session.save();
        }
        //Manual NodeType creation:
        /*NodeTypeTemplate type = nodeTypeManager.createNodeTypeTemplate();
        type.setName("ns:NodeType");
        type.setDeclaredSuperTypeNames(new String[] { "ns:ParentType1", "ns:ParentType2" });
        type.setAbstract(true);
        type.setOrderableChildNodes(true);
        type.setMixin(true);
        type.setQueryable(true);
        type.setPrimaryItemName("ex:property");*/
    }

    private void createIndexes(SimpleCredentials adminCredentials) throws LoginException, NoSuchWorkspaceException, IOException, CommitFailedException {
        try (ContentSession crsession = contentRepository.login(adminCredentials, null)) {
            Root croot = crsession.getLatestRoot();

            IndexDefinitionBuilder idxb = esHost.isPresent() ? new ElasticIndexDefinitionBuilder() : new IndexDefinitionBuilder();
            idxb.async("async");
            IndexRule rule = idxb.indexRule("qo:content");
            rule.property("title", "qo:title").nodeScopeIndex().analyzed().propertyIndex();
            rule.property("exclude", FulltextIndexConstants.REGEX_ALL_PROPS, true).excludeFromAggregation().disable();

            rule = idxb.indexRule("nt:file");
            rule.property("nodeName", ":nodeName").nodeScopeIndex().propertyIndex().disable();
            rule.property("exclude", FulltextIndexConstants.REGEX_ALL_PROPS, true).excludeFromAggregation().propertyIndex().disable();

            rule = idxb.indexRule("qo:resource");
            rule.property("data", "jcr:data").nodeScopeIndex().propertyIndex().disable();
            rule.property("exclude", FulltextIndexConstants.REGEX_ALL_PROPS, true).excludeFromAggregation().propertyIndex().disable();
                       
            rule = idxb.indexRule("nt:resource");
            rule.property("exclude", FulltextIndexConstants.REGEX_ALL_PROPS, true).excludeFromAggregation().propertyIndex().disable();
           

            //aggregate at the file level. It is too challenging to aggregate at the parent content level due to duplicate fulltext values on the content and file nodes.
            AggregateRule aggregate = idxb.aggregateRule("qo:content");
            aggregate.include("include0").path("*");
            aggregate.include("include1").path("*/jcr:content");
            //AggregateRule aggregate = idxb.aggregateRule("nt:file");
            //aggregate.include("include0").path("*/jcr:content");

            Tree index = croot.getTree("/").addChild(IndexConstants.INDEX_DEFINITIONS_NAME).addChild("qoContent");
            idxb.build(index);
            index.getChild("aggregates").getChild("qo:content").setProperty("reaggregateLimit", 0L);
            index.getChild("aggregates").getChild("qo:content").getChild("include0").setProperty("primaryType", "nt:file");
            index.getChild("aggregates").getChild("qo:content").getChild("include1").setProperty("primaryType", "qo:resource");

            
            //index.getChild("aggregates").getChild("nt:file").getChild("include1").setProperty("primaryType", "qo:resource");
            

            /*          

            rule.property("data", "jcr:data").nodeScopeIndex();
            rule.property("exclude", FulltextIndexConstants.REGEX_ALL_PROPS, true).excludeFromAggregation().disable();
            
            rule = idxb.indexRule("nt:resource");
            rule.property("exclude", FulltextIndexConstants.REGEX_ALL_PROPS, true).excludeFromAggregation().disable();
            
            AggregateRule aggregate = idxb.aggregateRule("qo:content");
            aggregate.include("include0").path("*");
            aggregate = idxb.aggregateRule("nt:file");
            aggregate.include("include0").path("jcr:content");
            
            Tree index = croot.getTree("/").addChild(IndexConstants.INDEX_DEFINITIONS_NAME).addChild("qoContent");
            idxb.build(index);
            index.getChild("aggregates").getChild("qo:content").setProperty("reaggregateLimit", 0L);
            index.getChild("aggregates").getChild("nt:file").setProperty("reaggregateLimit", 0L);
            index.getChild("aggregates").getChild("qo:content").getChild("include0").setProperty("primaryType", "nt:file");
            index.getChild("aggregates").getChild("nt:file").getChild("include0").setProperty("primaryType", "qo:resource");*/

            //The Markdown MIME type is not supported by the current version of Tika so instruct Oak to map it to plain/text 
            Tree tika = index.addChild(FulltextIndexConstants.TIKA);
            tika.setProperty(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED, Type.NAME);
            tika = tika.addChild(FulltextIndexConstants.TIKA_MIME_TYPES);
            tika.setProperty(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED, Type.NAME);
            tika = tika.addChild("text");
            tika.setProperty(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED, Type.NAME);
            tika = tika.addChild("x-web-markdown");
            tika.setProperty(JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED, Type.NAME);
            tika.setProperty(FulltextIndexConstants.TIKA_MAPPED_TYPE, "text/plain");

            croot.commit();
        }
    }

    private void populateRepository(Node root, Session session, Group general) throws RepositoryException {
        root.addMixin("qo:content");
        root.setProperty("qo:title", "Main");
        addFile(root, "md/main.md", "content.md", "text/x-web-markdown", "main", true, true, session);
        AccessControlUtils.addAccessControlEntry(session, root.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_READ, Privilege.JCR_ADD_CHILD_NODES, Privilege.JCR_REMOVE_CHILD_NODES }, true);

        //String id =UUID.randomUUID().toString();
        Node category = root.addNode(RandomStringUtils.random(10, true, true));
        category.addMixin("qo:content");
        category.setProperty("qo:title", "Java");
        addFile(category, "md/java.md", "content.md", "text/x-web-markdown", "main", true, true, session);
        AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

        category = category.addNode(RandomStringUtils.random(10, true, true));
        category.addMixin("qo:content");
        category.setProperty("qo:title", "Quarkus");
        addFile(category, "md/quarkus.md", "content.md", "text/x-web-markdown", "main", true, true, session);
        addFile(category, "md/quarkus.pdf", "quarkus.pdf", "application/pdf", "attachment", false, false, session);
        addFile(category, "md/quarkus.jpg", "quarkus.jpg", "image/jpg", "image", false, false, session);
        AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

        category = root.addNode(RandomStringUtils.random(10, true, true));
        category.addMixin("qo:content");
        category.setProperty("qo:title", "JavaScript");
        addFile(category, "md/javascript.md", "content.md", "text/x-web-markdown", "main", true, true, session);
        AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

        category = root.addNode(RandomStringUtils.random(10, true, true));
        category.addMixin("qo:content");
        category.setProperty("qo:title", "CSS");
        addFile(category, "md/css.md", "content.md", "text/x-web-markdown", "main", true, true, session);
        AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, true);

        //Will not be visible because the ACL denies it
        category = root.addNode(RandomStringUtils.random(10, true, true));
        category.addMixin("qo:content");
        category.setProperty("qo:title", "Protected");
        addFile(category, "md/protected.md", "content.md", "text/x-web-markdown", "main", true, true, session);
        AccessControlUtils.addAccessControlEntry(session, category.getPath(), general.getPrincipal(), new String[] { Privilege.JCR_ALL }, false);
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

    static void addFile(Node folderNode, String resourcePath, String fileName, String mimeType, String fileType, boolean index, boolean isText, Session session) throws RepositoryException {
        Node fileNode = folderNode.addNode(fileName, JcrConstants.NT_FILE);
        fileNode.addMixin("qo:file");
        fileNode.setProperty("qo:fileType", fileType);
        Node resNode = fileNode.addNode(JcrConstants.JCR_CONTENT, index ? "qo:resource" : JcrConstants.NT_RESOURCE);
        resNode.setProperty(JcrConstants.JCR_MIMETYPE, mimeType);
        if (isText) {
            resNode.setProperty(JcrConstants.JCR_ENCODING, "UTF-8");
        }
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        Binary contentValue = session.getValueFactory().createBinary(is);
        resNode.setProperty(JcrConstants.JCR_DATA, contentValue);
        Calendar lastModified = Calendar.getInstance();
        resNode.setProperty(JcrConstants.JCR_LASTMODIFIED, lastModified);
    }

    @Produces
    public Repository repository() {
        return repository;
    }

    @Produces
    public ContentRepository contentRepository() {
        return contentRepository;
    }

    void onShutdown(@Observes ShutdownEvent ev) {
        //It is very important to cleanly shutdown the filestore/AWS persistence. Otherwise a lengthy recovery will occur on the next startup.
        if (fileStore != null) {
            fileStore.close();
        }

    }

}
