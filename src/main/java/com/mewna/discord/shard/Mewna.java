package com.mewna.discord.shard;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.CatnipOptions;
import com.mewna.catnip.cache.CacheFlag;
import com.mewna.catnip.cache.EntityCacheWorker;
import com.mewna.catnip.cache.UnifiedMemoryEntityCache;
import com.mewna.catnip.entity.user.Presence;
import com.mewna.catnip.entity.user.Presence.Activity;
import com.mewna.catnip.entity.user.Presence.ActivityType;
import com.mewna.catnip.entity.user.Presence.OnlineStatus;
import com.mewna.catnip.shard.CatnipShard.ShardConnectState;
import com.mewna.catnip.shard.manager.DefaultShardManager;
import com.mewna.catnip.shard.manager.ShardCondition;
import com.mewna.catnip.util.SafeVertxCompletableFuture;
import io.sentry.Sentry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @author infinity
 * @since 12/23/18
 */
@Accessors(fluent = true)
public final class Mewna {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    private final Map<Integer, Catnip> catnips = new ConcurrentHashMap<>();
    
    public static void main(final String[] args) {
        new Mewna().start();
    }
    
    private void start() {
        if(System.getenv("SENTRY_DSN") != null) {
            Sentry.init(System.getenv("SENTRY_DSN"));
        }
        
        final EntityCacheWorker sharedCache = new UnifiedMemoryEntityCache();
        final int count = Integer.parseInt(System.getenv("SHARD_COUNT"));
        logger.info("Will be starting {} shards!", count);
        
        for(int i = 0; i < count; i++) {
            final Catnip catnip = provideCatnip(i, count, sharedCache);
            catnips.put(i, catnip);
            new MewnaShard(this, catnip).start();
            logger.info("Started shard {} / {}", i, count);
            try {
                Thread.sleep(6500L);
            } catch(final InterruptedException e) {
                Sentry.capture(e);
            }
            logger.info("Preparing to start shard {} / {}", i + 1, count);
        }
    }
    
    private Catnip provideCatnip(final int id, final int count, final EntityCacheWorker cache) {
        return Catnip.catnip(new CatnipOptions(System.getenv("TOKEN"))
                        .cacheWorker(cache)
                        .presence(Presence.of(OnlineStatus.ONLINE, Activity.of("mewna.com", ActivityType.PLAYING)))
                        .cacheFlags(EnumSet.of(CacheFlag.DROP_EMOJI, CacheFlag.DROP_GAME_STATUSES))
                        .shardManager(new DefaultShardManager(count, Collections.singletonList(id))),
                Vertx.vertx());
    }
}
