package org.wallet.rogalik.mcp.server.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.wallet.rogalik.mcp.platform.Platform;
import org.wallet.rogalik.mcp.server.MCPProtocol;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class GetCrashReportsTool implements McpTool {

    private static final int DEFAULT_REPORTS = 3;
    private static final int MAX_REPORT_CHARS = 20000;

    @Override
    public String name() {
        return "get_crash_reports";
    }

    @Override
    public String description() {
        return "List and read the most recent crash reports, plus optionally the tail of latest.log.\n\n"
            + "Use this after the game or server dies, when the in-memory log buffer went down with "
            + "it. For anything happening in a still-running game, get_logs and wait_for_log are "
            + "faster and searchable.";
    }

    @Override
    public JsonObject inputSchema() {
        return SchemaBuilder.object()
            .integer("count", "How many of the newest crash reports to return", false, DEFAULT_REPORTS)
            .bool("include_latest_log", "Also include the tail of logs/latest.log", false, false)
            .integer("log_tail_lines", "How many lines of latest.log to include", false, 200)
            .build();
    }

    @Override
    public JsonObject call(JsonObject arguments) {
        Path gameDir = Platform.get().gameDir();
        int count = arguments.has("count") ? Math.clamp(arguments.get("count").getAsInt(), 1, 10) : DEFAULT_REPORTS;

        JsonObject response = new JsonObject();
        JsonArray reports = new JsonArray();

        Path crashDir = gameDir.resolve("crash-reports");
        if (Files.isDirectory(crashDir)) {
            for (Path report : newestFiles(crashDir, count)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("file", gameDir.relativize(report).toString());
                try {
                    entry.addProperty("modifiedMs", Files.getLastModifiedTime(report).toMillis());
                    entry.addProperty("content", clip(Files.readString(report, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    entry.addProperty("error", "Could not read: " + e.getMessage());
                }
                reports.add(entry);
            }
        }

        response.add("crashReports", reports);
        response.addProperty("crashReportCount", reports.size());
        if (reports.isEmpty()) {
            response.addProperty("note", "No crash reports found - the game has not crashed.");
        }

        if (arguments.has("include_latest_log") && arguments.get("include_latest_log").getAsBoolean()) {
            int lines = arguments.has("log_tail_lines")
                ? Math.clamp(arguments.get("log_tail_lines").getAsInt(), 1, 2000)
                : 200;
            response.addProperty("latestLog", readTail(gameDir.resolve("logs").resolve("latest.log"), lines));
        }

        return MCPProtocol.createSuccessResponse(response.toString());
    }

    private static List<Path> newestFiles(Path directory, int count) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparingLong(GetCrashReportsTool::lastModified).reversed())
                .limit(count)
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String readTail(Path file, int lines) {
        if (!Files.isRegularFile(file)) {
            return "(no latest.log found)";
        }
        try (Stream<String> stream = Files.lines(file, StandardCharsets.UTF_8)) {
            List<String> all = new ArrayList<>(stream.toList());
            List<String> tail = all.size() > lines ? all.subList(all.size() - lines, all.size()) : all;
            return clip(String.join("\n", tail));
        } catch (IOException e) {
            return "(could not read latest.log: " + e.getMessage() + ")";
        }
    }

    private static String clip(String content) {
        if (content.length() <= MAX_REPORT_CHARS) {
            return content;
        }
        return content.substring(0, MAX_REPORT_CHARS)
            + "\n... (truncated, " + content.length() + " chars total)";
    }
}
