package net.draycia.carbon.storage;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface UserService {

    ChatUser wrap(String name);
    ChatUser wrap(OfflinePlayer player);
    ChatUser wrap(UUID uuid);

    @Nullable
    default ChatUser wrapIfLoaded(OfflinePlayer player) {
        return wrapIfLoaded(player.getUniqueId());
    }

    @Nullable
    ChatUser wrapIfLoaded(UUID uuid);

    ChatUser refreshUser(UUID uuid);

    void onDisable();

    void invalidate(ChatUser user);
    void validate(ChatUser user);

}
