package net.draycia.carbon.api.config;

import org.spongepowered.configurate.serialize.ConfigSerializable;

@ConfigSerializable
public enum MessagingType {
  NONE,
  REDIS,
  BUNGEECORD
}