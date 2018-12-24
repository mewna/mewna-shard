package com.mewna.discord.shard;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.CatnipOptions;
import com.mewna.catnip.cache.CacheFlag;
import com.mewna.catnip.cache.EntityCacheWorker;
import com.mewna.catnip.cache.MemoryEntityCache;
import com.mewna.catnip.shard.manager.DefaultShardManager;
import io.sentry.Sentry;
import io.vertx.core.Vertx;

import java.util.Collections;
import java.util.EnumSet;

/**
 * @author infinity
 * @since 12/23/18
 */
public final class Mewna {
    private final Vertx vertx = Vertx.vertx();
    
    public static void main(final String[] args) {
        new Mewna().start();
    }
    
    private void start() {
        final EntityCacheWorker sharedCache = new MemoryEntityCache();
        final int count = Integer.parseInt(System.getenv("SHARD_COUNT"));
        
        for(int i = 0; i < count; i++) {
            new MewnaShard(provideCatnip(i, count, sharedCache)).start();
            try {
                Thread.sleep(6500L);
            } catch(final InterruptedException e) {
                Sentry.capture(e);
            }
        }
    }
    
    private Catnip provideCatnip(final int id, final int count, final EntityCacheWorker cache) {
        return Catnip.catnip(new CatnipOptions(System.getenv("TOKEN"))
                        .cacheWorker(cache)
                        .cacheFlags(EnumSet.of(CacheFlag.DROP_EMOJI, CacheFlag.DROP_GAME_STATUSES))
                        .shardManager(new DefaultShardManager(count, Collections.singletonList(id))),
                vertx);
    }
}
