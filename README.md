# Qoakus

A  Apache Jackrabbit Oak reference web application with an AWS backend running on Quarkus.

## Features

### View
Examine JCR node properties and related nodes. Binary file nodes containing Markdown are rendered in the browser.

![view](images/view.png)

#### Hierarchical Navigation
Navigate down and up the JCR node hierarchy.

![navigate](images/navigate.png)

#### Tika Preview
Binary file nodes can be previewed using Apache Tika. Note that document formats will not be fully preserved as Tika is intended to be used primarily for content indexing and not full fidelity document conversion rendering.

![preview](images/preview1.png)

### Edit

#### Markdown Editor
A Javascript Markdown editor is used to manage node primary binary content inside the browser.

![preview](images/edit.png)

#### Image Upload

Images can be dragged and dropped to the Markdown editor or manually uploaded.

#### Attachment Upload

File attachments can be uploaded and stored as binary JCR file nodes.

#### Outlook Drag and Drop

Email messages from the Windows Outlook client can be dragged and dropped to the mailbox icon and they will be automatically uploaded as binary JCR file nodes. The messages will be stored in the Outlook propriety .msg format but they can be previewed via Tika. The Outlook web application will not drop a full binary file but it does drop metadata that could be used retrieve the binary message from Outlook using the Microsoft Graph API.

## Security

### External Identity Provider

A Jackrabbit Oak [External Identity Provider](http://jackrabbit.apache.org/oak/docs/security/authentication/externalloginmodule.html) with credentials support for [Quarkus JWT tokens](https://quarkus.io/guides/security-openid-connect) is included for OIDC SSO. Edit the [application.properties](src/main/resources/application.properties) to configure and enable this feature.

### ACL
A group, quarkus-general, is automatically created and assigned full permissions to all JCR root nodes except for a protected one setup for demonstration purposes.

### User Management
The [ResourceManager.java](src/main/java/com/github/aaronanderson/qoakus/RepositoryManager.java) class includes API calls for creating users and groups and assigning permissions to them.


## AWS

### Custom Node Segment

The [oak-segment-aws](https://github.com/apache/jackrabbit-oak/tree/trunk/oak-segment-aws) plugin is used to permanently persist JCR nodes in the Cloud. If no AWS configurations are present in the application.properties file the JCR repository will be created using in-memory storage.


#### Setup

Update the [application.properties](src/main/resources/application.properties) file and provide values for the qoakus.aws properties. The `qoakus.aws.filestore-path` property is optional since nothing gets written to it and a temporary directy will be used if a value is not provided.

Jackrabbit Oak will automatically create the specified S3 bucket and DynamoDB tables if they do not exists. Take special care when setting these values and starting the Quarkus application for the first time.

## Build and Run

Build the application:

`mvn clean install`

Run the application in Quarkus devmode:

`mvn quarkus:dev`

The application will be available at `http://localhost:5000`

While Quarkus is running the Snowpack devserver can run concurrently for real-time TypeScript updates:

`yarn start`

The application will be available at `http://localhost:8080`

To run Quarkus in production mode start the runner jar:

`java -jar target/quarkus-app/quarkus-run.jar`


## Maintenance

[Jackrabbit Oak CLI](http://jackrabbit.apache.org/oak/docs/command_line.html)

`mvn dependency:copy -Dartifact="org.apache.jackrabbit:oak-run:RELEASE:jar" -Dmdep.stripVersion -DoutputDirectory=/tmp/`

`java -jar /tmp/oak-run.jar export`
