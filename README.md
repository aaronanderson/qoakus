# Qoakus

In development. Sample Apache Jackrabbit Oak web application with an AWS backend running on Quarkus.

## Features

### View

#### Hierarchical Navigation

#### Tika Preview

### Edit

#### Markdown Editor

#### Image Upload

#### Attachment Upload

#### Outlook Drag and Drop

## Security

## AWS Setup

## Build and Run



## Maintenance

[Jackrabbit Oak CLI](http://jackrabbit.apache.org/oak/docs/command_line.html)

`mvn dependency:copy -Dartifact="org.apache.jackrabbit:oak-run:RELEASE:jar" -Dmdep.stripVersion -DoutputDirectory=/tmp/`

`java -jar /tmp/oak-run.jar export`
