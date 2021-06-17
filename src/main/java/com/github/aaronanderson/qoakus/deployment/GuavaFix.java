package com.github.aaronanderson.qoakus.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

//Edit the Jackrabbit Oak document classes to prevent Gauva compatibility issues documented here: https://issues.apache.org/jira/browse/OAK-7182
//This is a build time fix that avoid building a Quarkus extension and all of the related overhead. https://quarkus.io/guides/class-loading-reference#reading-class-bytecode
//Note the QuarkusClassLoader used in Devmode bans the target/classes directories so this fix will only work in production mode.
public class GuavaFix {

    public static void main(String[] args) {
        try {
            System.out.format("Applying Apache Jackrabbit Oak Guava fix to  %s\n", args[0]);

            Path outDir = Paths.get(args[0]);

            fix("org.apache.jackrabbit.oak.plugins.document.DocumentNodeStoreBuilder", outDir, new Remapper() {
                @Override
                public String mapMethodName(final String owner, final String name, final String descriptor) {
                    return "sameThreadExecutor".equals(name) ? "newDirectExecutorService" : super.mapMethodName(owner, name, descriptor);
                }
            });

            Remapper toStringMapper = new Remapper() {
                @Override
                public String map(String internalName) {
                    return internalName.replace("com/google/common/base/Objects", "com/google/common/base/MoreObjects");
                }
            };
            fix("org.apache.jackrabbit.oak.cache.AbstractCacheStats", outDir, toStringMapper);
            fix("org.apache.jackrabbit.oak.plugins.blob.StagingCacheStats", outDir, toStringMapper);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private static void fix(String className, Path outDir, Remapper remapper) throws IOException {

        String classFileName = className.replace('.', '/') + ".class";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        InputStream classIn = cl.getResourceAsStream(classFileName);

        ClassReader reader = new ClassReader(classIn);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        ClassVisitor classRemapper = new ClassRemapper(writer, remapper);

        reader.accept(classRemapper, 0);
        byte[] classBytes = writer.toByteArray();

        Path outFile = outDir.resolve(classFileName);
        Files.createDirectories(outFile.getParent());
        Files.write(outFile, classBytes);

    }

}
