package dev.walshy.blobrepo;

import dev.walshy.blobrepo.obj.Repo;
import dev.walshy.blobrepo.utils.Utils;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
public final class Config {

    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    public static final Config INSTANCE = new Config();

    private int port;
    private long expiryTime;

    private boolean displayInfo;
    private String repoName;
    private String repoId;
    private String displayedUrl;

    private boolean ipWhitelistEnabled;
    private final Set<String> ipWhitelist = new HashSet<>();

    private final Set<Repo> repos = new HashSet<>();

    @SuppressWarnings("unchecked")
    private Config() {
        createDefaultConfig();

        try {
            final File f = new File("config.yml");
            final Yaml yaml = new Yaml();
            String configStr = String.join("\n", Files.readAllLines(f.toPath(),
                StandardCharsets.UTF_8));

            if (configStr.indexOf('\t') != -1) {
                configStr = configStr.replace("\t", "  ");
                logger.warn("Config contains a tab! Please look into replacing it with normal spaces!");
            }
            if (configStr.contains("AUTH_KEY")) {
                while (configStr.contains("AUTH_KEY")) {
                    configStr = configStr.replaceFirst("AUTH_KEY", Utils.generateKey());
                }
                logger.info("Replaced default 'AUTH_KEY' with randomly generated ones");

                try (FileWriter fw = new FileWriter(f)) {
                    fw.write(configStr);
                    fw.flush();
                }
            }

            Map<String, Object> map = yaml.load(configStr);

            this.port = (int) map.getOrDefault("port", 8888);
            final Object expiryTimeObject = map.getOrDefault("expiryTime", 600);
            this.expiryTime = expiryTimeObject instanceof Integer ? (int) expiryTimeObject : (long) expiryTimeObject;

            final Map<String, Object> whitelist = (Map<String, Object>)
                map.getOrDefault("ipWhitelist", new HashMap<>());
            this.ipWhitelistEnabled = (boolean) whitelist.getOrDefault("enabled", false);
            this.ipWhitelist.addAll((Collection<String>) whitelist.getOrDefault("ips", new HashSet<>()));

            this.displayInfo = (boolean) map.getOrDefault("displayInfo", true);
            this.repoName = (String) map.getOrDefault("repoName", "Example Repo");
            this.repoId = (String) map.getOrDefault("repoId", "example-repo");
            this.displayedUrl = (String) map.getOrDefault("displayedUrl", "https://repo.example.com");

            final Map<String, Map<String, Object>> repoSection = (Map<String, Map<String, Object>>)
                map.getOrDefault("repos", new HashMap<>());
            for (Map.Entry<String, Map<String, Object>> id : repoSection.entrySet()) {
                final Map<String, Object> info = id.getValue();
                String path = (String) info.getOrDefault("path", "/");
                if (path.charAt(0) != '/')
                    path = '/' + path;
                if (path.charAt(path.length() - 1) != '/')
                    path = path + '/';

                this.repos.add(new Repo(id.getKey(), path,
                    (String) info.getOrDefault("readAuth", null),
                    (String) info.getOrDefault("writeAuth", null)
                ));
            }

            // Just add a default one if none exist and send a warning message
            if (this.repos.isEmpty()) {
                final String writeKey = Utils.generateKey();
                logger.warn("No valid repos found in the config! Adding a default of '/', no read auth " +
                    "and a write key of: {}", writeKey);
                this.repos.add(new Repo("example", "/", null, writeKey));
            }
        } catch (IOException e) {
            logger.error("Failed to load config.yml!", e);
            System.exit(1);
        }
    }

    private void createDefaultConfig() {
        final File config = new File("config.yml");
        if (config.exists()) return;

        try {
            Files.copy(getClass().getResourceAsStream("/config.yml"), config.toPath());
        } catch (IOException e) {
            logger.error("Failed to copy default config! {}", e.getMessage());
            System.exit(1);
        }
    }
}
