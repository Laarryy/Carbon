package net.draycia.carbon.common.channels;

import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.channels.WhisperChannel;
import net.draycia.carbon.api.events.ChatComponentEvent;
import net.draycia.carbon.api.events.ChatFormatEvent;
import net.draycia.carbon.api.events.misc.CarbonEvents;
import net.draycia.carbon.api.users.CarbonUser;
import net.draycia.carbon.api.users.ConsoleUser;
import net.draycia.carbon.api.users.PlayerUser;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CarbonWhisperChannel implements WhisperChannel {

  private @NonNull final CarbonChat carbonChat;

  private @NonNull final PlayerUser sender;
  private @NonNull final PlayerUser audience;

  public CarbonWhisperChannel(@NonNull final PlayerUser sender, @NonNull final PlayerUser audience) {
    this.carbonChat = CarbonChatProvider.carbonChat();
    this.sender = sender;
    this.audience = audience;
  }

  @Override
  public @NonNull PlayerUser sender() {
    return this.sender;
  }

  @Override
  public @NonNull PlayerUser audience() {
    return this.audience;
  }

  @Override
  public @Nullable TextColor channelColor(@NonNull final CarbonUser user) {
    return NamedTextColor.WHITE;
  }

  @Override
  public @NonNull Map<CarbonUser, Component> parseMessage(@NonNull final PlayerUser user, @NonNull final String message,
                                                          final boolean fromRemote) {
    return this.parseMessage(user, Collections.singleton(this.audience()), message, fromRemote);
  }

  @Override
  public @NonNull Map<CarbonUser, Component> parseMessage(@NonNull final PlayerUser user,
                                                          @NonNull final Collection<@NonNull PlayerUser> recipients,
                                                          @NonNull final String message, final boolean fromRemote) {
    final Map<CarbonUser, Component> result = new HashMap<>();

    // Formats
    final String senderFormat = this.senderFormat();
    final String receiverFormat = this.receiverFormat();

    final ChatFormatEvent senderFormatEvent = new ChatFormatEvent(this.sender, this.sender, this, senderFormat, message);

    CarbonEvents.post(senderFormatEvent);

    if (senderFormatEvent.cancelled() || senderFormatEvent.message().trim().isEmpty()) {
      return Collections.emptyMap();
    }

    final ChatFormatEvent receiverFormatEvent = new ChatFormatEvent(this.sender, this.audience, this, receiverFormat, message);

    CarbonEvents.post(receiverFormatEvent);

    if (receiverFormatEvent.cancelled() || receiverFormatEvent.message().trim().isEmpty()) {
      return Collections.emptyMap();
    }

    // Display names
    final String senderName;

    if (this.sender.nickname() != null) {
      senderName = this.sender.nickname();
    } else {
      senderName = this.sender.displayName();
    }

    final String receiverName;

    if (this.audience.nickname() != null) {
      receiverName = this.audience.nickname();
    } else {
      receiverName = this.audience.displayName();
    }

    // Components
    final TextComponent senderComponent = (TextComponent) this.carbonChat.messageProcessor().processMessage(
      senderFormatEvent.format(),
      "senderdisplayname", senderName,
      "sender", this.sender.name(),
      "receiverdisplayname", receiverName,
      "receiver", this.audience.name(),
      "phase", Long.toString(System.currentTimeMillis() % 25),
      "message", senderFormatEvent.message());

    final ChatComponentEvent senderComponentEvent = new ChatComponentEvent(this.sender, this.sender, this,
      senderComponent, senderFormatEvent.message());

    CarbonEvents.post(senderComponentEvent);

    result.put(this.sender, senderComponentEvent.component());

    final TextComponent receiverComponent = (TextComponent) this.carbonChat.messageProcessor().processMessage(
      receiverFormatEvent.format(),
      "senderdisplayname", senderName,
      "sender", this.sender.name(),
      "receiverdisplayname", receiverName,
      "receiver", this.audience.name(),
      "phase", Long.toString(System.currentTimeMillis() % 25),
      "message", senderFormatEvent.message());

    final ChatComponentEvent receiverComponentEvent = new ChatComponentEvent(this.sender, this.audience, this,
      receiverComponent, receiverFormatEvent.message());

    CarbonEvents.post(receiverComponentEvent);

    result.put(this.audience, receiverComponentEvent.component());

    final ChatFormatEvent consoleFormatEvent = new ChatFormatEvent(this.sender, null, this,
      this.consoleFormat(), message);

    CarbonEvents.post(consoleFormatEvent);

    final TextComponent consoleFormat = (TextComponent) this.carbonChat.messageProcessor().processMessage(
      consoleFormatEvent.format(),
      "senderdisplayname", senderName,
      "sender", this.sender.name(),
      "receiverdisplayname", receiverName,
      "receiver", this.audience.name(),
      "phase", Long.toString(System.currentTimeMillis() % 25),
      "message", senderFormatEvent.message());

    final ChatComponentEvent consoleEvent = new ChatComponentEvent(this.sender, null, this, consoleFormat,
      consoleFormatEvent.message());

    CarbonEvents.post(consoleEvent);

    if (!consoleEvent.cancelled()) {
      result.put(this.carbonChat.userService().consoleUser(), consoleEvent.component());
    }

    this.sender.replyTarget(this.audience);
    this.audience.replyTarget(this.sender);

    return result;
  }

  @Override
  public boolean canPlayerUse(@NonNull final PlayerUser user) {
    return user.equals(this.sender);
  }

  @Override
  public boolean canPlayerSee(@NonNull final PlayerUser sender, @NonNull final PlayerUser target, final boolean checkSpying) {
    return (checkSpying && target.spyingWhispers()) || (sender.equals(this.sender) && target.equals(this.audience));
  }

  @Override
  public boolean canPlayerSee(@NonNull final PlayerUser target, final boolean checkSpying) {
    return false;
  }

  @Override
  public void sendComponents(@NonNull final Identity identity,
                             @NonNull final Map<? extends CarbonUser, Component> components) {
    for (final Map.Entry<? extends CarbonUser, Component> entry : components.entrySet()) {
      entry.getKey().sendMessage(identity, entry.getValue());
    }
  }

  @Override
  public void sendComponentsAndLog(@NonNull final Identity identity,
                                   @NonNull final Map<? extends CarbonUser, Component> components) {
    for (final Map.Entry<? extends CarbonUser, Component> entry : components.entrySet()) {
      entry.getKey().sendMessage(Identity.nil(), entry.getValue());

      if (entry.getKey() instanceof ConsoleUser) {
        this.carbonChat.messageProcessor().audiences().console()
          .sendMessage(identity, entry.getValue());
      }
    }
  }

  @Override
  public @NonNull String name() {
    return "Whisper";
  }

  @Override
  public @NonNull String key() {
    return "whisper";
  }

  @Override
  public boolean ignorable() {
    return false;
  }

  @Override
  public @NonNull List<@NonNull Pattern> itemLinkPatterns() {
    return this.carbonChat.carbonSettings().itemLinkPatterns();
  }

  public @Nullable String senderFormat() {
    return this.carbonChat.carbonSettings().whisperOptions().senderFormat();
  }

  public @Nullable String receiverFormat() {
    return this.carbonChat.carbonSettings().whisperOptions().receiverFormat();
  }

  public @Nullable String consoleFormat() {
    return this.carbonChat.carbonSettings().whisperOptions().consoleFormat();
  }

  @Override
  public @Nullable String switchMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().switchMessage();
  }

  @Override
  public @Nullable String switchOtherMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().switchOtherMessage();
  }

  @Override
  public @Nullable String switchFailureMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().switchFailureMessage();
  }

  @Override
  public @Nullable String cannotIgnoreMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().cannotIgnoreMessage();
  }

  @Override
  public @Nullable String toggleOffMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().toggleOffMessage();
  }

  @Override
  public @Nullable String toggleOnMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().toggleOnMessage();
  }

  @Override
  public @Nullable String toggleOtherOnMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().toggleOtherOnMessage();
  }

  @Override
  public @Nullable String toggleOtherOffMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().toggleOtherOffMessage();
  }

  @Override
  public @Nullable String cannotUseMessage() {
    return this.carbonChat.carbonSettings().whisperOptions().cannotUseMessage();
  }
}