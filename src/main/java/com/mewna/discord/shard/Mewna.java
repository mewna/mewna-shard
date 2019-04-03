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
import com.mewna.catnip.shard.manager.DefaultShardManager;
import io.sentry.Sentry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
            if(i < count - 1) {
                logger.info("Preparing to start shard {} / {}", i + 1, count);
            } else {
                logger.info("Finished booting shards! :tada:");
                System.gc();
            }
        }
    }
    
    private Catnip provideCatnip(final int id, final int count, final EntityCacheWorker cache) {
        return Catnip.catnip(new CatnipOptions(System.getenv("TOKEN"))
                        .cacheWorker(cache)
                        .presence(Presence.of(OnlineStatus.ONLINE, Activity.of("mewna.com", ActivityType.PLAYING)))
                        .cacheFlags(EnumSet.of(CacheFlag.DROP_GAME_STATUSES, CacheFlag.DROP_EMOJI))
                        .shardManager(new DefaultShardManager(count, Collections.singletonList(id))),
                Vertx.vertx(new VertxOptions()
                        .setEventLoopPoolSize(2)
                        .setInternalBlockingPoolSize(5)
                        .setWorkerPoolSize(10)
                        .setClustered(false)
                ));
    }
}
