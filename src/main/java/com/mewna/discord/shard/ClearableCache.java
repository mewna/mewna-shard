package com.mewna.discord.shard;

import com.mewna.catnip.cache.MemoryEntityCache;

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
}
