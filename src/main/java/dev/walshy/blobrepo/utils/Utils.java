package dev.walshy.blobrepo.utils;

import dev.walshy.blobrepo.Config;
import dev.walshy.blobrepo.obj.Project;
import dev.walshy.blobrepo.obj.Repo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.HaltException;
import spark.Response;
import spark.Spark;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    private static final char[] keySet = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
        'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '-', '_', '.', '#'
    };

    private Utils() {}

    @Nonnull
    public static String[] split(@Nonnull String str, char ch) {
        int off = 0;
        int next;
        List<String> list = new ArrayList<>();
        while ((next = str.indexOf(ch, off)) != -1) {
            list.add(str.substring(off, next));
            off = next + 1;
        }
        // If no match was found, return this
        if (off == 0)
            return new String[] {str};

        // Add remaining segment
        list.add(str.substring(off));

        // Construct result
        int resultSize = list.size();
        while (resultSize > 0 && list.get(resultSize - 1).length() == 0)
            resultSize--;
        return list.subList(0, resultSize).toArray(new String[resultSize]);
    }

    @Nonnull
    public static String error(@Nonnull Response res, @Nonnull String error) {
        res.status(400);
        return "{\"error\": \"" + error + "\"}";
    }

    @Nonnull
    public static String error(@Nonnull Response res, int code, @Nonnull String error) {
        res.status(code);
        return "{\"error\": \"" + error + "\"}";
    }

    public static HaltException error(int code, @Nonnull String error) {
        return Spark.halt(code, "{\"error\": \"" + error + "\"}");
    }

    @Nonnull
    public static Repo getRepoFromPathInfo(@Nonnull String pathInfo) {
        Repo repo = null;
        for (Repo r : Config.INSTANCE.getRepos()) {
            if (pathInfo.startsWith(r.getPath())) {
                repo = r;
                break;
            }
        }
        if (repo == null) {
            throw Spark.halt(400, "{\"errors\": \"Unknown repo for path: " + pathInfo + "\"}");
        }
        return repo;
    }

    @Nonnull
    public static String generateKey() {
        final StringBuilder sb = new StringBuilder();
        final ThreadLocalRandom rand = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            sb.append(keySet[rand.nextInt(keySet.length)]);
        }
        return sb.toString();
    }

    public static File getRootDir(@Nonnull Repo repo, @Nonnull Project project) {
        final File f = new File("public" + repo.getPath().replace('/', File.separatorChar)
            + project.toString().replace('.', File.separatorChar));
        if (!f.exists() && !f.mkdirs()) {
            logger.warn("Unable to create dirs needed for {}", f.getAbsolutePath());
        }

        return f;
    }

    public static File getDir(@Nonnull Repo repo, @Nonnull Project project) {
        final File f = new File(getRootDir(repo, project), project.getVersion());
        if (!f.exists() && !f.mkdirs()) {
            logger.warn("Unable to create dirs needed for {}", f.getAbsolutePath());
        }

        return f;
    }

    public static void write(@Nonnull File f, @Nullable String content) {
        if (content == null) return;

        try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
            fw.write(content);
            fw.flush();
        } catch (IOException e) {
            logger.error("Failed to write {} - {}", f.getName(), e.getMessage(), e);
        }
    }

    public static String md5(@Nonnull File f) {
        return hash("MD5", f);
    }

    public static String sha1(@Nonnull File f) {
        return hash("SHA-1", f);
    }

    public static String sha2(@Nonnull File f) {
        return hash("SHA-256", f);
    }

    private static String hash(@Nonnull String algorithm, @Nonnull File f) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (FileInputStream in = new FileInputStream(f)) {
                int n = 0;
                byte[] buffer = new byte[8192];
                while (n != -1) {
                    n = in.read(buffer);
                    if (n > 0) {
                        digest.update(buffer, 0, n);
                    }
                }
                return hex(digest.digest());
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            logger.error("Failed to generate {} for {}", algorithm, f.getName(), e);
            return null;
        }
    }

    private static String hex(@Nonnull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
