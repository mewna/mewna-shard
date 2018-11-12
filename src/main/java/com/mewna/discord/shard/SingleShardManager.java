package com.mewna.discord.shard;

import com.google.common.collect.ImmutableList;
import com.mewna.catnip.Catnip;
import com.mewna.catnip.shard.CatnipShard;
import com.mewna.catnip.shard.CatnipShard.ShardConnectState;
import com.mewna.catnip.shard.manager.ShardManager;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author amy
 * @since 10/22/18.
 */
@Accessors(fluent = true)
public class SingleShardManager implements ShardManager {
    private final MewnaShard shard;
    @Getter
    private final AtomicBoolean booted = new AtomicBoolean(false);
    @Getter
    @Setter
    private int shardId;
    @Getter
    @Setter
    private int shardCount;
    @Getter
    @Setter
    private Catnip catnip;
    @Getter
    @Setter
    private Future<Boolean> nextFuture;
    
    SingleShardManager(final int shardId, final int shardCount, final MewnaShard shard) {
        this.shardId = shardId;
        this.shardCount = shardCount;
        this.shard = shard;
    }
    
    @Override
    public void start() {
        catnip.logAdapter().info("Booting 1 shard");
        // Deploy verticles
        // because each shard has its own presence, so no global presence on catnip class
        //noinspection TypeMayBeWeakened
        final CatnipShard shard = new CatnipShard(catnip, shardId, shardCount, catnip.initialPresence());
        catnip.vertx().deployVerticle(shard);
        catnip.logAdapter().info("Deployed shard {}", shardId);
        // Start verticles
        startShard();
    }
    
    private void startShard() {
        catnip.eventBus().<JsonObject>send(CatnipShard.controlAddress(shardId), new JsonObject().put("mode", "START"),
                reply -> {
                    if(reply.succeeded()) {
                        final ShardConnectState state = ShardConnectState.valueOf(reply.result().body().getString("state"));
                        switch(state) {
                            case READY: {
                                catnip.logAdapter().info("Connected shard {} with state {}", shardId, reply.result().body());
                                nextFuture.complete(true);
                                booted.set(true);
                                break;
                            }
                            case RESUMED: {
                                catnip.logAdapter().info("Connected shard {} with state {}", shardId, reply.result().body());
                                nextFuture.complete(false);
                                booted.set(true);
                                break;
                            }
                            case FAILED: {
                                catnip.logAdapter().warn("Failed connecting shard {}, re-queueing", shardId);
                                nextFuture.fail("Couldn't connect shard");
                                booted.set(false);
                                addToConnectQueue(shardId);
                                break;
                            }
                            default: {
                                catnip.logAdapter().error("Got unexpected / unknown shard connect state: {}", state);
                                nextFuture.fail("Couldn't connect shard");
                                booted.set(false);
                                addToConnectQueue(shardId);
                                break;
                            }
                        }
                    } else {
                        catnip.logAdapter().warn("Failed connecting shard {} entirely, re-queueing", shardId);
                        booted.set(false);
                        addToConnectQueue(shardId);
                    }
                });
    }
    
    @Override
    public void addToConnectQueue(final int i) {
        catnip.shutdown(false);
        booted.set(false);
        catnip.logAdapter().warn("Shutting down shard due to connect failure.");
        reconnect();
    }
    
    private void reconnect() {
        shard.lighthouse().service().lock().setHandler(lock -> {
            if(lock.succeeded() && lock.result()) {
                nextFuture = Future.future();
                start();
                nextFuture.setHandler(res -> {
                    if(res.result()) {
                        // Worked, READY
                        shard.lighthouse().vertx().setTimer(5500L, __ -> shard.lighthouse().service().unlock());
                        catnip.logAdapter().info("READY shard {}", shardId);
                    } else if(res.succeeded() && !res.result()) {
                        // Worked, RESUMED
                        shard.lighthouse().service().unlock();
                        catnip.logAdapter().info("RESUMED shard {}", shardId);
                    } else {
                        // Didn't work
                        shard.lighthouse().service().unlock();
                        catnip.logAdapter().warn("Couldn't start shard, trying again in 500ms...");
                        shard.lighthouse().vertx().setTimer(500L, __ -> reconnect());
                    }
                });
            } else {
                catnip.logAdapter().warn("Couldn't acquire sharding lock, trying again in 500ms...");
                shard.lighthouse().vertx().setTimer(500L, __ -> reconnect());
            }
        });
    }
    
    @Nonnull
    @Override
    public Future<List<String>> trace(final int shard) {
        final Future<List<String>> future = Future.future();
        catnip.eventBus().<JsonArray>send(CatnipShard.controlAddress(shard), new JsonObject().put("mode", "TRACE"),
                reply -> {
                    if(reply.succeeded()) {
                        // ow
                        future.complete(ImmutableList.copyOf(reply.result().body().stream()
                                .map(e -> (String) e).collect(Collectors.toList())));
                    } else {
                        future.fail(reply.cause());
                    }
                });
        return future;
    }
    
    @Override
    public void shutdown() {
        catnip.eventBus().send(CatnipShard.controlAddress(shardId), new JsonObject().put("mode", "SHUTDOWN"));
    }
}
