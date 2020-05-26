package dev.walshy.blobrepo;

import dev.walshy.blobrepo.obj.Project;
import dev.walshy.blobrepo.obj.Repo;
import dev.walshy.blobrepo.utils.Utils;
import dev.walshy.blobrepo.utils.XmlGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.staticfiles.StaticFilesConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class BlobRepo {

    private static final Logger logger = LoggerFactory.getLogger(BlobRepo.class);

    private final Map<String, StaticFilesConfiguration> staticRepos = new HashMap<>();

    public static void main(String[] args) {
        new BlobRepo().init();
    }

    private void init() {
        // Static file stuff
        for (Repo repo : Config.INSTANCE.getRepos()) {
            final StaticFilesConfiguration config = new StaticFilesConfiguration();
            config.configureExternal("public" + repo.getPath());
            config.setExpireTimeSeconds(Config.INSTANCE.getExpiryTime());

            this.staticRepos.put(repo.getId(), config);
        }

        // Spark routes and stuffs
        Spark.port(Config.INSTANCE.getPort());

        setupHandlers();
        setupBeforeAfter();

        setupDisplayInfo();

        setupGetRoute();
        setupPutRoute();

        Spark.awaitInitialization();
    }

    private void setupHandlers() {
        Spark.notFound((req, res) -> {
            res.status(404);
            return "Not Found";
        });
        Spark.internalServerError((req, res) -> {
            logger.error("Internal server error! {} {}", req.requestMethod(), req.pathInfo());

            res.status(500);
            return "Error occurred";
        });
    }

    private void setupBeforeAfter() {
        Spark.before("/*", (req, res) -> {
            if (Config.INSTANCE.isIpWhitelistEnabled() && !Config.INSTANCE.getIpWhitelist().contains(req.ip())) {
                throw Spark.halt(403);
            }

            String pathInfo = req.pathInfo();
            final long startMs = System.currentTimeMillis();
            req.attribute("startMs", startMs);

            // TODO: Do a proper fix. This is to allow accessing GET /
            if (pathInfo.length() == 1) return;

            final Repo repo = Utils.getRepoFromPathInfo(pathInfo);
            req.attribute("repo", repo);
            // Remove "/", "/public/", etc.
            pathInfo = pathInfo.substring(repo.getPath().length());

            final String[] parts = Utils.split(pathInfo, '/');
            final boolean metadata = parts[parts.length - 1].startsWith("maven-metadata.xml");
            // groupId, artifactId, version, artifactId-version.[pom|jar]
            if (parts.length < 4)
                throw Spark.halt(404, "Invalid path!");

            final String requestedContent = parts[parts.length - 1];
            final String version = metadata ? null : parts[parts.length - 2];
            final String artifactId = parts[parts.length - (metadata ? 2 : 3)];
            final String groupId = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - (metadata ? 2 : 3)));

            final Project project = new Project(groupId, artifactId, version);

            req.attribute("project", project);
            req.attribute("content", requestedContent);
            req.attribute("metadata", metadata);
        });

        Spark.after("/*", (req, res) -> {
            long total = System.currentTimeMillis() - (long) req.attribute("startMs");
            logger.info("[{}] {} Request to {} took {}ms", res.status(), req.requestMethod(), req.pathInfo(), total);

            res.header("Encoding", "gzip");
        });
    }

    private void setupDisplayInfo() {
        if (!Config.INSTANCE.isDisplayInfo()) return;

        Spark.get("/", (req, res) -> "<h2>" + Config.INSTANCE.getRepoName() + "<h2>"
            + "\n<hr>"
            + "\n<h3>Maven</h3>"
            + "\n<xmp lang=\"xml\">"
            + "<repository>"
            + "\n    <id>" + Config.INSTANCE.getRepoId() + "</id>"
            + "\n    <url>" + Config.INSTANCE.getDisplayedUrl() + "</url>"
            + "\n</repository>"
            + "</xmp>"
            + "\n<h3>Gradle</h3>"
            + "\n<pre lang=\"groovy\">"
            + "repositories {"
            + "\n    maven { url '" + Config.INSTANCE.getDisplayedUrl() + "' }"
            + "\n}"
            + "</pre>"
        );
    }

    private void setupGetRoute() {
        Spark.get("/*", (req, res) -> {
            final Repo repo = req.attribute("repo");

            validateAuth(req, repo.getReadAuth());

            final String requestedContent = req.attribute("content");
            final Project project = req.attribute("project");
            final StaticFilesConfiguration config = this.staticRepos.get(repo.getId());

            if (requestedContent.equals("maven-metadata.xml")
                && !new File(Utils.getRootDir(repo, project), "maven-metadata.xml").exists()
            ) {
                return XmlGenerator.generateDefaultMetadata(repo, project);
            }

            if (!config.consume(req.raw(), res.raw())) {
                return Utils.error(res, 404, "File not found");
            }
            return "";
        });
    }

    private void setupPutRoute() {
        Spark.put("/*", (req, res) -> {
            final Repo repo = req.attribute("repo");

            validateAuth(req, repo.getWriteAuth());

            final Project project = req.attribute("project");
            final String content = req.attribute("content");
            final boolean metadata = req.attribute("metadata");

            // Validate content a little
            validateHashes(req, content);

            final File f = new File(metadata ? Utils.getRootDir(repo, project) : Utils.getDir(repo, project), content);
            if (!f.exists() && !f.createNewFile()) {
                logger.warn("Unable to create file {}", f.getName());
                return Utils.error(res, "Unable to create file!");
            }

            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(req.bodyAsBytes());
                fos.flush();
            }
            logger.info("Finished writing {} bytes for {}", req.bodyAsBytes().length, project);

            res.status(200);
            return "OK";
        });
    }

    private void validateHashes(@Nonnull Request req, @Nonnull String content) {
        final byte[] data = req.bodyAsBytes();
        if ((content.endsWith(".md5") && data.length != 32)
            || (content.endsWith(".sha1") && data.length != 40)
            || (content.endsWith(".sha2") && data.length != 64)
        ) {
            throw Utils.error(400, "Invalid checksums!");
        }
    }

    private void validateAuth(@Nonnull Request req, @Nullable String expectedKey) {
        final String authHeader = req.headers("Authorization");
        if (expectedKey == null) return;

        if (authHeader == null || !authHeader.startsWith("Basic "))
            throw Utils.error(401, "Invalid Auth header");

        // Remove the "Basic " and decode.
        final String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
        // No user specified so just use the decoded string.
        final String decodedPassword = decoded.indexOf(':') == -1 ? decoded :
            // Get the actual password
            decoded.substring(decoded.indexOf(':') + 1);

        if (!decodedPassword.equals(expectedKey))
            throw Spark.halt(401);
    }
}
