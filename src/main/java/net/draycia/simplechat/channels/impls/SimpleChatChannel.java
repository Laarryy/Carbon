package net.draycia.simplechat.channels.impls;

import me.clip.placeholderapi.PlaceholderAPI;
import me.minidigger.minimessage.text.MiniMessageParser;
import me.minidigger.minimessage.text.MiniMessageSerializer;
import net.draycia.simplechat.SimpleChat;
import net.draycia.simplechat.channels.ChatChannel;
import net.draycia.simplechat.events.ChannelChatEvent;
import net.draycia.simplechat.util.DiscordWebhook;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.event.message.MessageCreateEvent;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SimpleChatChannel extends ChatChannel {

    private TextColor color;
    private long id;
    private Map<String, String> formats;
    private String webhook;
    private boolean isDefault;
    private boolean ignorable;
    private String name;
    private double distance;
    private String switchMessage;
    private String toggleOffMessage;
    private String toggleOnMessage;
    private boolean forwardFormatting;
    private boolean shouldBungee;
    private boolean filterEnabled;

    private DiscordWebhook discordWebhook = null;

    private SimpleChat simpleChat;

    private ArrayList<Pattern> itemPatterns;

    private SimpleChatChannel() { }

    SimpleChatChannel(TextColor color, long id, Map<String, String> formats, String webhook, boolean isDefault, boolean ignorable, String name, double distance, String switchMessage, String toggleOffMessage, String toggleOnMessage, boolean forwardFormatting, boolean shouldBungee, boolean filterEnabled, SimpleChat simpleChat) {
        this.color = color;
        this.id = id;
        this.formats = formats;
        this.webhook = webhook;
        this.isDefault = isDefault;
        this.ignorable = ignorable;
        this.name = name;
        this.distance = distance;
        this.switchMessage = switchMessage;
        this.toggleOffMessage = toggleOffMessage;
        this.toggleOnMessage = toggleOnMessage;
        this.forwardFormatting = forwardFormatting;
        this.shouldBungee = shouldBungee;
        this.filterEnabled = filterEnabled;

        this.simpleChat = simpleChat;

        if (getChannelId() > 0 && simpleChat.getDiscordManager().getDiscordAPI() != null) {
            simpleChat.getDiscordManager().getDiscordAPI().addMessageCreateListener(this::processDiscordMessage);
        }

        if (getWebhook() != null) {
            discordWebhook = new DiscordWebhook(getWebhook());
        }

        for (String entry : simpleChat.getConfig().getStringList("item-link-placeholders")) {
            itemPatterns.add(Pattern.compile(entry));
        }
    }

    SimpleChat getSimpleChat() {
        return simpleChat;
    }

    private String getDiscordFormatting() {
        return formats.getOrDefault("discord-to-mc", "<gray>[<blue>Discord<gray>] <username><white>: <message>");
    }

    @Override
    public boolean canPlayerUse(Player player) {
        return player.hasPermission("simplechat.channels." + getName() + ".use");
    }

    @Override
    public List<Player> getAudience(OfflinePlayer offlinePlayer) {
        List<Player> audience = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canPlayerSee(offlinePlayer, player)) {
                audience.add(player);
            }
        }

        return audience;
    }

    @Override
    public void processDiscordMessage(MessageCreateEvent event) {
        if (event.getChannel().getId() != getChannelId() || !event.getMessageAuthor().isRegularUser()) {
            return;
        }

        String message = MiniMessageParser.escapeTokens(event.getMessageContent()).replace("~", "\\~")
                .replace("_", "\\_").replace("*", "\\*").replace("\n", "");

        MessageAuthor author = event.getMessageAuthor();
        ServerTextChannel channel = (ServerTextChannel)event.getChannel();

        String role = "";

        if (author.asUser().isPresent() && event.getServer().isPresent()) {
            List<Role> roles = author.asUser().get().getRoles(event.getServer().get());

            if (!roles.isEmpty()) {
                role = roles.get(roles.size() - 1).getName();
            }
        }

        // Placeholders: username, displayname, channel, server, message, primaryrole
        Component component = MiniMessageParser.parseFormat(getDiscordFormatting(), "message", message,
                "username", author.getName(), "displayname", author.getDisplayName(), "channel", channel.getName(),
                "server", event.getServer().get().getName(), "primaryrole", role);

        for (Player player : getAudience(null)) {
            simpleChat.getAudiences().player(player).sendMessage(component);
        }
    }

    @Override
    public void sendMessage(OfflinePlayer player, String message) {
        // Get player's formatting
        String group;

        if (player.isOnline()) {
            group = simpleChat.getPermission().getPrimaryGroup(player.getPlayer());
        } else {
            group = simpleChat.getPermission().getPrimaryGroup(null, player);
        }

        String messageFormat = getFormat(group);

        // If the player isn't online (cross server message), use their normal name
        if (!player.isOnline()) {
            messageFormat = messageFormat.replace("%player_displayname%", "%player_name%");
        }

        // Chat filter
        if (filterEnabled() && simpleChat.getConfig().contains("filters")) {
            for (String entry : simpleChat.getConfig().getStringList("filters")) {
                message = message.replaceAll(entry, simpleChat.getConfig().getString("filter-text", "****"));
            }
        }

        // Chat placeholders
        ConfigurationSection replacements = simpleChat.getConfig().getConfigurationSection("replacements");

        if (replacements != null) {
            for (String key : replacements.getKeys(false)) {
                message = message.replace(key, replacements.getString(key));
            }
        }

        // Call custom chat event
        ChannelChatEvent event = new ChannelChatEvent(player, this, messageFormat, message);

        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        // Parse placeholders
        messageFormat = PlaceholderAPI.setPlaceholders(player, event.getFormat());

        // Convert legacy color codes to Mini color codes
        Component component = LegacyComponentSerializer.legacy('&').deserialize(messageFormat);
        messageFormat = MiniMessageSerializer.serialize(component);

        // First pass for placeholders, to support placeholders in placeholders
        messageFormat = MiniMessageParser.handlePlaceholders(messageFormat, "color", "<" + color.toString() + ">",
                "phase", Long.toString(System.currentTimeMillis() % 25), "server", simpleChat.getConfig().getString("server-name", "Server"));

        // Finally, parse remaining placeholders and parse format
        messageFormat = MiniMessageParser.handlePlaceholders(messageFormat, "message", event.getMessage());

        // Send message to players who can see it
        Component formattedMessage = MiniMessageParser.parseFormat(messageFormat);

        if (formattedMessage instanceof TextComponent && player.isOnline()) {
            for (Pattern pattern : itemPatterns) {
                formattedMessage = ((TextComponent) formattedMessage).replace(pattern, (input) -> {
                    return TextComponent.builder().append(simpleChat.getItemStackUtils().createComponent(player.getPlayer()));
                });
            }
        }

        if (simpleChat.isUserShadowMuted(player)) {
            if (player.isOnline()) {
                simpleChat.getAudiences().player(player.getPlayer()).sendMessage(formattedMessage);
            }
        } else {
            for (Player onlinePlayer : getAudience(player)) {
                simpleChat.getAudiences().player(onlinePlayer).sendMessage(formattedMessage);

                if (simpleChat.getConfig().getBoolean("pings.enabled")) {
                    if (message.toLowerCase().contains(onlinePlayer.getName().toLowerCase())) {
                        String sound = simpleChat.getConfig().getString("pings.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
                        float volume = (float)simpleChat.getConfig().getDouble("pings.volume");
                        float pitch = (float)simpleChat.getConfig().getDouble("pings.pitch");

                        onlinePlayer.playSound(onlinePlayer.getLocation(), sound, volume, pitch);
                    }
                }
            }
        }

        // Log message to console
        System.out.println(LegacyComponentSerializer.legacy().serialize(formattedMessage));

        // Route message to bungee / discord (if message originates from this server)
        // Use instanceof and not isOnline, if this message originates from another then the instanceof will
        // fail, but isOnline may succeed if the player is online on both servers (somehow).
        if (player instanceof Player) {
            if (shouldForwardFormatting() && shouldBungee()) {
                sendMessageToBungee(player.getPlayer(), component);
            } else if (shouldBungee()) {
                sendMessageToBungee(player.getPlayer(), message);
            }

            sendMessageToDiscord(player, message);
        }
    }

    @Override
    public void sendComponent(OfflinePlayer player, Component component) {
        for (Player onlinePlayer : getAudience(player)) {
            simpleChat.getAudiences().player(onlinePlayer).sendMessage(component);
        }

        System.out.println(LegacyComponentSerializer.legacy().serialize(component));
    }

    public void sendMessageToDiscord(OfflinePlayer player, String message) {
        if (discordWebhook == null) {
            return;
        }

        discordWebhook.setUsername(player.getName());
        discordWebhook.setContent(message.replace("@", "@\u200B"));
        discordWebhook.setAvatarUrl("https://minotar.net/helm/" + player.getUniqueId().toString() + "/100.png");

        try {
            discordWebhook.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessageToBungee(Player player, Component component) {
        simpleChat.getPluginMessageManager().sendComponent(this, player, component);
    }

    public void sendMessageToBungee(Player player, String message) {
        simpleChat.getPluginMessageManager().sendMessage(this, player, message);
    }

    public boolean canPlayerSee(@CheckForNull OfflinePlayer sender, Player target) {
        if (!target.hasPermission("simplechat." + getName().toLowerCase() + ".see")) {
            return false;
        }

        if (sender instanceof Player) {
            if (getDistance() > 0 && target.getLocation().distance(((Player)sender).getLocation()) > getDistance()) {
                return false;
            }
        }

        if (isIgnorable() && sender != null) {
            if (simpleChat.playerHasPlayerIgnored(sender, target) && !target.hasPermission("simplechat.ignoreexempt")) {
                return false;
            }

            if (simpleChat.playerHasChannelMuted(target, this)) {
                return false;
            }
        }

        return true;
    }

    public Map<String, String> getFormats() {
        return formats;
    }

    @Override
    public TextColor getColor() {
        return color;
    }

    @Override
    public long getChannelId() {
        return id;
    }

    @Override
    public String getFormat(String group) {
        return getFormats().getOrDefault(group, getFormats().getOrDefault("default", "<white><%player_displayname%<white>> <message>"));
    }

    @Override
    public String getWebhook() {
        return webhook;
    }

    @Override
    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public boolean isIgnorable() {
        return ignorable;
    }

    @Override
    public boolean shouldBungee() {
        return shouldBungee;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getDistance() {
        return distance;
    }

    @Override
    public String getSwitchMessage() {
        return switchMessage;
    }

    @Override
    public String getToggleOffMessage() {
        return toggleOffMessage;
    }

    @Override
    public String getToggleOnMessage() {
        return toggleOnMessage;
    }

    @Override
    public boolean shouldForwardFormatting() {
        return forwardFormatting;
    }

    @Override
    public boolean filterEnabled() {
        return filterEnabled;
    }

    public static SimpleChatChannel.Builder builder(String name) {
        return new SimpleChatChannel.Builder(name);
    }

    public static class Builder extends ChatChannel.Builder {

        private TextColor color = TextColor.of(255, 255, 255);
        private long id = -1;
        private Map<String, String> formats = new HashMap<>();
        private String webhook = null;
        private boolean isDefault = false;
        private boolean ignorable = true;
        private String name;
        private double distance = -1;
        private String switchMessage = "<gray>You are now in <color><channel> <gray>chat!";
        private String toggleOffMessage = "<gray>You can no longer see <color><channel> <gray>chat!";
        private String toggleOnMessage = "<gray>You can now see <color><channel> <gray>chat!";
        private boolean forwardFormatting = true;
        private boolean shouldBungee = false;
        private boolean filterEnabled = true;

        private Builder() { }

        Builder(String name) {
            this.name = name.toLowerCase();
        }

        @Override
        public SimpleChatChannel build(SimpleChat simpleChat) {
            return new SimpleChatChannel(color, id, formats, webhook, isDefault, ignorable, name, distance, switchMessage, toggleOffMessage, toggleOnMessage, forwardFormatting, shouldBungee, filterEnabled, simpleChat);
        }

        @Override
        public Builder setColor(TextColor color) {
            this.color = color;

            return this;
        }

        @Override
        public Builder setColor(String color) {
            return setColor(TextColor.fromHexString(color));
        }

        @Override
        public Builder setId(long id) {
            this.id = id;

            return this;
        }

        @Override
        public Builder setFormats(Map<String, String> formats) {
            this.formats = formats;

            return this;
        }

        @Override
        public Builder setWebhook(String webhook) {
            this.webhook = webhook;

            return this;
        }

        @Override
        public Builder setIsDefault(boolean aDefault) {
            this.isDefault = aDefault;

            return this;
        }

        @Override
        public Builder setIgnorable(boolean ignorable) {
            this.ignorable = ignorable;

            return this;
        }

        @Override
        public Builder setName(String name) {
            this.name = name;

            return this;
        }

        @Override
        public Builder setDistance(double distance) {
            this.distance = distance;

            return this;
        }

        @Override
        public Builder setSwitchMessage(String switchMessage) {
            this.switchMessage = switchMessage;

            return this;
        }

        @Override
        public Builder setToggleOffMessage(String toggleOffMessage) {
            this.toggleOffMessage = toggleOffMessage;

            return this;
        }

        @Override
        public Builder setToggleOnMessage(String toggleOnMessage) {
            this.toggleOnMessage = toggleOnMessage;

            return this;
        }

        @Override
        public Builder setShouldForwardFormatting(boolean forwardFormatting) {
            this.forwardFormatting = forwardFormatting;

            return this;
        }

        @Override
        public Builder setShouldBungee(boolean shouldBungee) {
            this.shouldBungee = shouldBungee;

            return this;
        }

        @Override
        public Builder setFilterEnabled(boolean filterEnabled) {
            this.filterEnabled = filterEnabled;

            return this;
        }
    }

}
