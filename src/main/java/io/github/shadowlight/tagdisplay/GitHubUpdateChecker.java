package io.github.shadowlight.tagdisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubUpdateChecker {
    private static final String UPDATE_PERMISSION = "sltagdisplay.update-notify";
    private static final Pattern JSON_STRING = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");

    private final SLTagDisplayPlugin plugin;
    private final AtomicBoolean checkInProgress = new AtomicBoolean();
    private volatile boolean stopped;
    private volatile String latestVersion;
    private volatile String releaseUrl;

    GitHubUpdateChecker(SLTagDisplayPlugin plugin) {
        this.plugin = plugin;
    }

    void checkAsync() {
        if (stopped || !plugin.getConfig().getBoolean("update-check.enabled", true)
                || !checkInProgress.compareAndSet(false, true)) {
            return;
        }
        String repository = plugin.getConfig().getString("update-check.repository", "").trim();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            checkInProgress.set(false);
            plugin.getLogger().fine("Update checking is disabled until update-check.repository is set to owner/repository.");
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection connection = null;
            try {
                URI endpoint = URI.create("https://api.github.com/repos/" + repository + "/releases/latest");
                connection = (HttpURLConnection) endpoint.toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "SLTagDisplay/" + plugin.getDescription().getVersion());
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    plugin.getLogger().fine("GitHub update check returned HTTP " + connection.getResponseCode() + ".");
                    return;
                }

                String json;
                try (InputStream input = connection.getInputStream()) {
                    json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
                String tag = jsonString(json, "tag_name");
                String url = jsonString(json, "html_url");
                String currentVersion = plugin.getDescription().getVersion();
                if (tag.isBlank() || url.isBlank() || compareVersions(tag, currentVersion) <= 0) {
                    return;
                }
                String normalized = tag.replaceFirst("(?i)^v-?", "");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (stopped || !plugin.isEnabled() || !plugin.getConfig().getBoolean("update-check.enabled", true)) return;
                    latestVersion = normalized;
                    releaseUrl = url;
                    plugin.getLogger().info("SL Tag Display " + latestVersion + " is available. Current version: "
                            + currentVersion + ". Release: " + releaseUrl);
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        notifyIfAvailable(player);
                    }
                });
            } catch (Exception exception) {
                plugin.getLogger().fine("GitHub update check failed: " + exception.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
                checkInProgress.set(false);
            }
        });
    }

    void notifyIfAvailable(Player player) {
        String version = latestVersion;
        String url = releaseUrl;
        if (version == null || url == null || !player.hasPermission(UPDATE_PERMISSION)) return;
        player.sendMessage(Component.text("[SL Tag Display] Version " + version + " is available. ")
                .append(Component.text("Open the GitHub release page.").clickEvent(ClickEvent.openUrl(url))));
    }

    void stop() {
        stopped = true;
    }

    private String jsonString(String json, String key) {
        Matcher matcher = JSON_STRING.matcher(json);
        while (matcher.find()) {
            if (matcher.group(1).equals(key)) {
                return matcher.group(2).replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return "";
    }

    private int compareVersions(String candidate, String current) {
        List<Long> candidateParts = versionParts(candidate);
        List<Long> currentParts = versionParts(current);
        if (candidateParts.isEmpty() || currentParts.isEmpty()) return 0;
        for (int index = 0; index < Math.max(candidateParts.size(), currentParts.size()); index++) {
            long candidatePart = index < candidateParts.size() ? candidateParts.get(index) : 0L;
            long currentPart = index < currentParts.size() ? currentParts.get(index) : 0L;
            int compared = Long.compare(candidatePart, currentPart);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private List<Long> versionParts(String version) {
        Matcher matcher = Pattern.compile("\\d+").matcher(version.replaceFirst("(?i)^v-?", ""));
        List<Long> parts = new ArrayList<>();
        while (matcher.find()) {
            try {
                parts.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
                return List.of();
            }
        }
        return parts;
    }
}
