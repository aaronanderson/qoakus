package com.github.aaronanderson.qoakus;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeTypeManager;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.segment.file.FileStore;
import org.jboss.logging.Logger;

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

            Oak oak = new Oak();
            oak.with("qoakus");

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

            NodeTypeManager mgr = session.getWorkspace().getNodeTypeManager();
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

            if (!mgr.hasNodeType("qo:ContentType")) {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("cnd/content.cnd");
                CndImporter.registerNodeTypes(new InputStreamReader(is), session);

                Node root = session.getRootNode();
                root.addMixin("qo:content");
                root.setProperty("qo:title", "Main");
                addFile(root, "md/main.md", "content.md", "text/markdown", "main", session);

                //String id =UUID.randomUUID().toString();
                Node category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "Java");
                addFile(category, "md/java.md", "content.md", "text/markdown", "main", session);
                //addFile(category, "md/java.pdf", "java.pdf", "application/pdf", "attachment", session);

                category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "JavaScript");
                addFile(category, "md/javascript.md", "content.md", "text/markdown", "main", session);

                category = root.addNode(RandomStringUtils.random(10, true, true));
                category.addMixin("qo:content");
                category.setProperty("qo:title", "CSS");
                addFile(category, "md/css.md", "content.md", "text/markdown", "main", session);

                session.save();

            }

        } catch (Throwable t) {
            logger.error("Oak repository error", t);
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
