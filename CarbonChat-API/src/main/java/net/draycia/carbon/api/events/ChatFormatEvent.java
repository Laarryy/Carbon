package net.draycia.carbon.api.events;

import net.draycia.carbon.api.channels.ChatChannel;
import net.draycia.carbon.api.events.misc.CarbonEvent;
import net.draycia.carbon.api.users.PlayerUser;
import net.kyori.event.Cancellable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ChatFormatEvent implements CarbonEvent, Cancellable {

  private boolean isCancelled = false;
  private @NonNull final PlayerUser sender;
  private @NonNull final PlayerUser target;
  private @NonNull ChatChannel chatChannel;
  private @NonNull String format;
  private @NonNull String message;

  public ChatFormatEvent(@NonNull final PlayerUser sender, @Nullable final PlayerUser target,
                         @NonNull final ChatChannel chatChannel, @Nullable final String format,
                         @NonNull final String message) {

    this.sender = sender;
    this.target = target;

    this.chatChannel = chatChannel;
    this.format = format;
    this.message = message;
  }

  @Override
  public boolean cancelled() {
    return this.isCancelled;
  }

  @Override
  public void cancelled(final boolean cancelled) {
    this.isCancelled = cancelled;
  }

  public @NonNull PlayerUser sender() {
    return this.sender;
  }

  public @Nullable PlayerUser target() {
    return this.target;
  }

  public @NonNull ChatChannel channel() {
    return this.chatChannel;
  }

  public void channel(@NonNull final ChatChannel chatChannel) {
    this.chatChannel = chatChannel;
  }

  public @Nullable String format() {
    return this.format;
  }

  public void format(@Nullable final String format) {
    this.format = format;
  }

  public @NonNull String message() {
    return this.message;
  }

  public void message(@NonNull final String message) {
    this.message = message;
  }

}