package dev.walshy.blobrepo.utils;

import dev.walshy.blobrepo.obj.Project;
import dev.walshy.blobrepo.obj.Repo;

import javax.annotation.Nonnull;
import java.io.File;

public final class XmlGenerator {

    private static final String METADATA = "maven-metadata.xml";

    private XmlGenerator() {}

    public static String generateDefaultMetadata(@Nonnull Repo repo, @Nonnull Project project) {
        final String emptyXml = "<metadata modelVersion=\"1.1.0\">"
            + "\n    <groupId>" + project.getGroupId() + "</groupId>"
            + "\n    <artifactId>" + project.getArtifactId() + "</artifactId>"
            + "\n    <version>" + project.getVersion() + "</version>"
            + "\n</metadata>";

        final File rootDir = Utils.getRootDir(repo, project);
        final File metadata = new File(rootDir, METADATA);
        Utils.write(metadata, emptyXml);
        Utils.write(new File(rootDir, METADATA + ".md5"), Utils.md5(metadata));
        Utils.write(new File(rootDir, METADATA + ".sha1"), Utils.sha1(metadata));
        Utils.write(new File(rootDir, METADATA + ".sha2"), Utils.sha2(metadata));

        return emptyXml;
    }
}
