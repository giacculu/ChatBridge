package org.giacculu.chatBridge;

import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.util.logging.Logger;

public class ChatBridge extends JavaPlugin implements Listener {

    private String chatWebhookUrl;
    private String commandWebhookUrl;
    private String consoleWebhookUrl;
    private String deathWebhookUrl;
    private String connWebhookUrl;

    private String chatFormat;
    private String joinFormat;
    private String quitFormat;
    private String consoleFormat;
    private String commandFormat;
    private String deathFormat;

    public static Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();

        // Carica il config
        saveDefaultConfig();
        loadConfig();

        // Registra eventi
        Bukkit.getPluginManager().registerEvents(this, this);

        logger.info("ChatBridge enabled!");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();

        chatWebhookUrl = config.getString("chat-discord-webhook", "");
        connWebhookUrl = config.getString("connection-discord-webhook", "");
        commandWebhookUrl = config.getString("command-discord-webhook", "");
        consoleWebhookUrl = config.getString("console-discord-webhook", "");
        deathWebhookUrl = config.getString("death-discord-webhook", "");

        chatFormat = config.getString("chat-format", "**%player%:** %message%");
        joinFormat = config.getString("join-format", "✅ **%player%** has joined the server!");
        quitFormat = config.getString("quit-format", "❌ **%player%** has left the server!");
        consoleFormat = config.getString("console-format", "🖥️ [Console]: %message%");
        commandFormat = config.getString("command-format", "⌨️ [Command] **%player%**: %message%");
        deathFormat = config.getString("death-format", "💀 %message%");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        sendToDiscord(MessageType.CHAT, event.getPlayer().getName(), event.getMessage(), chatWebhookUrl);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendToDiscord(MessageType.JOIN, event.getPlayer().getName(), null, connWebhookUrl);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendToDiscord(MessageType.QUIT, event.getPlayer().getName(), null, connWebhookUrl);
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        sendToDiscord(MessageType.CONSOLE, null, event.getCommand(), consoleWebhookUrl);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        sendToDiscord(MessageType.COMMAND, event.getPlayer().getName(), event.getMessage(), commandWebhookUrl);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null || deathMessage.isEmpty()) return;

        sendToDiscord(MessageType.DEATH, event.getEntity().getName(), deathMessage, deathWebhookUrl);
    }

    private void sendToDiscord(MessageType type, String player, String message, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL is not set!");
            return;
        }

        String formattedMessage;

        switch (type) {
            case CHAT -> formattedMessage = chatFormat
                    .replace("%player%", player)
                    .replace("%message%", message);
            case JOIN -> formattedMessage = joinFormat.replace("%player%", player);
            case QUIT -> formattedMessage = quitFormat.replace("%player%", player);
            case CONSOLE -> formattedMessage = consoleFormat.replace("%message%", message);
            case COMMAND -> formattedMessage = commandFormat
                    .replace("%player%", player)
                    .replace("%message%", message);
            case DEATH -> formattedMessage = deathFormat
                    .replace("%player%", player)
                    .replace("%message%", message);
            default -> formattedMessage = message;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.addRequestProperty("Content-Type", "application/json");
                connection.addRequestProperty("User-Agent", "Minecraft-ChatBridge");
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");

                String jsonPayload = "{\"content\":\"" + formattedMessage.replace("\"", "\\\"") + "\"}";

                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(jsonPayload.getBytes());
                    stream.flush();
                }

                connection.getInputStream().close();
                connection.disconnect();
            } catch (Exception e) {
                logger.warning("Failed to send message to Discord: " + e.getMessage());
            }
        });
    }

    private enum MessageType {
        CHAT,
        JOIN,
        QUIT,
        CONSOLE,
        COMMAND,
        DEATH
    }
}
