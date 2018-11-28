package com.mewna.discord.shard;

import com.mewna.catnip.cache.MemoryEntityCache;
import com.mewna.catnip.entity.guild.Guild;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author amy
 * @since 10/27/18.
 */
public class ClearableCache extends MemoryEntityCache {
    public void clear() {
        guildCache.clear();
        userCache.clear();
        memberCache.clear();
        roleCache.clear();
        channelCache.clear();
        emojiCache.clear();
        voiceStateCache.clear();
        presenceCache.clear();
        selfUser.set(null);
    }
    
    @Nonnegative
    public int guildCount() {
        return guildCache.size();
    }
}
