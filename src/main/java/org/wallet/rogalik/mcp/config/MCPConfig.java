package org.wallet.rogalik.mcp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.wallet.rogalik.mcp.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MCPConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MCPConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of(
        "fill", "clone", "setblock", "summon", "tp", "give", "gamemode",
        "effect", "enchant", "weather", "time", "say", "tell", "title"
    );

    private ServerConfig server = new ServerConfig();
    private ClientConfig client = new ClientConfig();
    private SafetyConfig safety = new SafetyConfig();
    private InputConfig input = new InputConfig();
    private LogsConfig logs = new LogsConfig();
    private FilesConfig files = new FilesConfig();
    private FakePlayersConfig fakePlayers = new FakePlayersConfig();
    private AuthConfig auth = new AuthConfig();

    public static MCPConfig load() {
        Path configDir = Platform.get().configDir();
        String configFileName = "mcp.json";
        Path configFile = configDir.resolve(configFileName);

        // Backwards compatibility with mcp-client.json
        Path oldConfigFile = configDir.resolve("mcp-client.json");
        if (Files.exists(oldConfigFile) && !Files.exists(configFile)) {
            try {
                Files.move(oldConfigFile, configFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to rename mcp-client.json to mcp.json", e);
                configFile = oldConfigFile;
            }
        }
        
        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                MCPConfig loaded = GSON.fromJson(json, MCPConfig.class);
                if (loaded != null) {
                    loaded.fillMissingSections();
                    return loaded;
                }
                LOGGER.warn("Config file {} is empty or null, using defaults", configFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to load config file, using defaults", e);
            }
        }

        MCPConfig defaultConfig = new MCPConfig();
        defaultConfig.save();
        return defaultConfig;
    }
    
    public void save() {
        Path configDir = Platform.get().configDir();
        Path configFile = configDir.resolve("mcp.json");
        
        try {
            Files.createDirectories(configDir);
            String json = GSON.toJson(this);
            Files.writeString(configFile, json);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file", e);
        }
    }
    
    public ServerConfig getServer() { return server; }
    public ClientConfig getClient() { return client; }
    public SafetyConfig getSafety() { return safety; }
    public InputConfig getInput() { return input; }
    public LogsConfig getLogs() { return logs; }
    public FilesConfig getFiles() { return files; }
    public FakePlayersConfig getFakePlayers() { return fakePlayers; }
    public AuthConfig getAuth() { return auth; }

    /**
     * Gson leaves fields absent from the JSON at their initializer value, but a section written
     * as an explicit {@code null} in the file would come back null. Normalize so callers never
     * have to null-check a section.
     */
    private void fillMissingSections() {
        if (server == null) server = new ServerConfig();
        if (client == null) client = new ClientConfig();
        if (safety == null) safety = new SafetyConfig();
        if (input == null) input = new InputConfig();
        if (logs == null) logs = new LogsConfig();
        if (files == null) files = new FilesConfig();
        if (fakePlayers == null) fakePlayers = new FakePlayersConfig();
        if (auth == null) auth = new AuthConfig();
    }

    public static class ServerConfig {
        private String transport = "http"; // "stdio" or "http"
        private int port = 8080;
        private String host = "localhost";
        private boolean enableSafety = true;
        private int maxAreaSize = 10;
        private List<String> allowedCommands = DEFAULT_ALLOWED_COMMANDS;
        private int requestTimeoutMs = 30000;
        private boolean autoStart = true;
        
        public String getTransport() { return transport; }
        public int getPort() { return port; }
        public String getHost() { return host; }
        public boolean isEnableSafety() { return enableSafety; }
        public int getMaxAreaSize() { return maxAreaSize; }
        public List<String> getAllowedCommands() { return allowedCommands; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public boolean isAutoStart() { return autoStart; }
    }
    
    public static class ClientConfig {
        private boolean showNotifications = true;
        private String logLevel = "INFO";
        private boolean logCommands = false;
        private boolean saveScreenshotsForDebug = false;
        
        public boolean isShowNotifications() { return showNotifications; }
        public String getLogLevel() { return logLevel; }
        public boolean isLogCommands() { return logCommands; }
        public boolean isSaveScreenshotsForDebug() { return saveScreenshotsForDebug; }
    }
    
    public static class SafetyConfig {
        private int maxEntitiesPerCommand = 10;
        private int maxBlocksPerCommand = 125000;
        private boolean blockCreativeForAll = true;
        private boolean requireOpForAdminCommands = true;

        public int getMaxEntitiesPerCommand() { return maxEntitiesPerCommand; }
        public int getMaxBlocksPerCommand() { return maxBlocksPerCommand; }
        public boolean isBlockCreativeForAll() { return blockCreativeForAll; }
        public boolean isRequireOpForAdminCommands() { return requireOpForAdminCommands; }
    }

    /** Simulated keyboard and mouse input, and screen navigation. */
    public static class InputConfig {
        private boolean enabled = true;
        /** Guards clicks on widgets whose label looks like world/pack deletion. */
        private boolean allowDestructiveUi = false;
        /** Upper bound on how long a single key may be held, in client ticks. */
        private int maxHoldTicks = 200;
        /**
         * Stops singleplayer pausing when the window loses focus. Without this, working in
         * another window puts the pause menu over the view, and every screenshot and screen
         * inspection reports the pause menu instead of the game.
         */
        private boolean preventPauseOnLostFocus = true;

        public boolean isEnabled() { return enabled; }
        public boolean isAllowDestructiveUi() { return allowDestructiveUi; }
        public int getMaxHoldTicks() { return maxHoldTicks; }
        public boolean isPreventPauseOnLostFocus() { return preventPauseOnLostFocus; }
    }

    /** In-memory log capture exposed through get_logs / wait_for_log. */
    public static class LogsConfig {
        private boolean enabled = true;
        private int bufferSize = 5000;
        /** Cap for the blocking wait_for_log tool, independent of requestTimeoutMs. */
        private int maxWaitMs = 60000;
        private boolean captureChat = true;
        private String minLevel = "INFO";
        /**
         * Request/response bodies are truncated to this many characters before being logged.
         * Without it a single take_screenshot writes a megabyte of base64 into latest.log.
         */
        private int maxBodyLogChars = 2000;

        public boolean isEnabled() { return enabled; }
        public int getBufferSize() { return bufferSize; }
        public int getMaxWaitMs() { return maxWaitMs; }
        public boolean isCaptureChat() { return captureChat; }
        public String getMinLevel() { return minLevel; }
        public int getMaxBodyLogChars() { return maxBodyLogChars; }
    }

    /** Sandboxed access to files under the game directory. */
    public static class FilesConfig {
        private boolean enabled = true;
        private List<String> allowedRoots = List.of(
            "saves", "config", "datapacks", "resourcepacks", "logs", "crash-reports"
        );
        private boolean allowDelete = false;
        private long maxFileSizeBytes = 2 * 1024 * 1024L;

        public boolean isEnabled() { return enabled; }
        public List<String> getAllowedRoots() { return allowedRoots; }
        public boolean isAllowDelete() { return allowDelete; }
        public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    }

    /** Server-side fake players used to test multiplayer behaviour. */
    public static class FakePlayersConfig {
        private boolean enabled = true;
        private int maxCount = 8;

        public boolean isEnabled() { return enabled; }
        public int getMaxCount() { return maxCount; }
    }

    /** Optional bearer token for the HTTP endpoint. */
    public static class AuthConfig {
        private String token = null;

        public String getToken() { return token; }
        public boolean isEnabled() { return token != null && !token.isBlank(); }
    }
}