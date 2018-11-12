package com.mewna.discord.shard;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.CatnipOptions;
import com.mewna.catnip.cache.CacheFlag;
import com.mewna.catnip.entity.Entity;
import com.mewna.catnip.entity.Snowflake;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.guild.Guild;
import com.mewna.catnip.entity.guild.Member;
import com.mewna.catnip.entity.guild.Role;
import com.mewna.catnip.entity.message.MessageType;
import com.mewna.catnip.entity.user.User;
import com.mewna.catnip.entity.user.VoiceState;
import com.mewna.catnip.shard.CatnipShard;
import com.mewna.catnip.shard.DiscordEvent;
import com.mewna.catnip.shard.DiscordEvent.Raw;
import com.mewna.catnip.shard.GatewayOp;
import com.mewna.lighthouse.Lighthouse;
import gg.amy.singyeong.QueryBuilder;
import gg.amy.singyeong.SingyeongClient;
import gg.amy.singyeong.SingyeongType;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.escoffier.vertx.completablefuture.VertxCompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author amy
 * @since 10/22/18.
 */
@Accessors(fluent = true)
@SuppressWarnings("unused")
public final class MewnaShard {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    private Lighthouse lighthouse;
    private SingyeongClient client;
    private Catnip catnip;
    private boolean handlersRegistered;
    @Getter
    private int lastShardId = -1;
    @Getter
    private int lastLimit = -1;
    
    private MewnaShard() {
    }
    
    public static void main(final String[] args) {
        new MewnaShard().start();
    }

    private void start() {
        logger.info("Starting Mewna shard...");
        final int shardCount = Integer.parseInt(System.getenv("SHARD_COUNT"));
        if(shardCount <= 0) {
            throw new IllegalStateException("shard count " + shardCount + " <= 0!!!");
        }
        final String redisHost = System.getenv("REDIS_HOST");
        final String redisAuth = System.getenv("REDIS_AUTH");
        final int healthPort = Integer.parseInt(Optional.ofNullable(System.getenv("PORT"))
                .orElseGet(() -> "" + (9000 + new Random().nextInt(1000))));
        logger.info("Running healthcheck on port {}...", healthPort);
        lighthouse = Lighthouse.lighthouse(shardCount, healthPort, redisHost, redisAuth,
                this::handleSharding, this::handlePubsub);
        client = new SingyeongClient(System.getenv("SINGYEONG_DSN"), lighthouse.vertx(), "mewna-shard");
        client.connect()
                .thenAccept(__ -> {
                    client.onEvent(dispatch -> {
                        final JsonObject d = dispatch.data();
                        final String nonce = dispatch.nonce();
                        if(d.containsKey("type")) {
                            switch(d.getString("type").toUpperCase()) {
                                case "VOICE_JOIN": {
                                    if(lastShardId > -1) {
                                        lighthouse.vertx().eventBus().send(CatnipShard.websocketMessageQueueAddress(lastShardId),
                                                CatnipShard.basePayload(GatewayOp.VOICE_STATE_UPDATE, new JsonObject()
                                                        .put("guild_id", d.getString("guild_id"))
                                                        .put("channel_id", d.getString("channel_id"))
                                                        .put("self_deaf", true)
                                                        .put("self_mute", false)));
                                    }
                                    break;
                                }
                                case "VOICE_LEAVE": {
                                    if(lastShardId > -1) {
                                        //noinspection ConstantConditions
                                        final JsonObject json = new JsonObject()
                                                .put("type", "VOICE_LEAVE")
                                                .put("guild_id", d.getString("guild_id"));
                                        //logger.info("Sending to nekomimi node:\n{}", json.encodePrettily());
                                        client.send("nekomimi", new QueryBuilder().build(), json);
                                        lighthouse.vertx().eventBus().send(CatnipShard.websocketMessageQueueAddress(lastShardId),
                                                CatnipShard.basePayload(GatewayOp.VOICE_STATE_UPDATE, new JsonObject()
                                                        .put("guild_id", d.getString("guild_id"))
                                                        .putNull("channel_id")
                                                        .put("self_deaf", true)
                                                        .put("self_mute", false)));
                                    }
                                    break;
                                }
                                case "CACHE": {
                                    final JsonObject query = d.getJsonObject("query");
                                    final CompletableFuture<Collection<JsonObject>> collectionFuture = cacheLookup(query);
                                    collectionFuture.thenAccept(res -> {
                                        final Optional<JsonObject> first = res.stream().filter(e -> !e.isEmpty()).findFirst();
                                        final JsonObject cacheResult = first.orElse(new JsonObject());
                                        // Since routing is effectively random, broadcast to maximize chance of the
                                        // sender hearing it
                                        client.broadcast("mewna-backend", nonce, new QueryBuilder().build(), cacheResult);
                                    });
                                    break;
                                }
                            }
                        }
                    });
                    client.onInvalid(invalid -> {
                        logger.warn("Got invalid:");
                        logger.warn(invalid.reason());
                    });
                    logger.info("Connected to singyeong!");
                    lighthouse.init().setHandler(res -> {
                        if(res.succeeded()) {
                            logger.info("Started lighthouse!");
                            startSharding();
                        } else {
                            logger.error("Couldn't start lighthouse!", res.cause());
                        }
                    });
                })
                .exceptionally(e -> {
                    logger.error("Couldn't connect to singyeong", e);
                    System.exit(0);
                    return null;
                });
    }
    
    private void startSharding() {
        if(catnip != null) {
            if(((SingleShardManager) catnip.shardManager()).booted().get()) {
                logger.warn("Got request to start sharding, but we're already booted!",
                        new Throwable("Asked to shard when already sharded!"));
                return;
            }
        }
        lighthouse.startShard().setHandler(res -> {
            if(res.succeeded()) {
                logger.info("Fully booted!");
            } else {
                logger.error("Couldn't start shard!", res.cause());
            }
        });
    }
    
    private Future<Boolean> handleSharding(final int id, final int limit) {
        logger.info("Received shard id {} / {}", id, limit);
        final Future<Boolean> future = Future.future();
        
        // Don't initialize multiple times
        if(catnip == null) {
            catnip = Catnip.catnip(new CatnipOptions(System.getenv("TOKEN"))
                            .shardManager(new SingleShardManager(id, limit, this))
                            .cacheFlags(EnumSet.of(CacheFlag.DROP_EMOJI, CacheFlag.DROP_GAME_STATUSES))
                            .cacheWorker(new ClearableCache()),
                    lighthouse.vertx());
            registerHandlers(catnip, id);
        }
        if(id != lastShardId && lastShardId != -1) {
            // Clean cache to avoid leaking memes
            logger.warn("New shard {} != old={}, clearing cache!", id, lastShardId);
            ((ClearableCache) catnip.cacheWorker()).clear();
        }
        ((SingleShardManager) catnip.shardManager()).shardId(id);
        ((SingleShardManager) catnip.shardManager()).shardCount(limit);
        ((SingleShardManager) catnip.shardManager()).nextFuture(future);
        lastShardId = id;
        lastLimit = limit;
        catnip.startShards();
        
        return future;
    }
    
    @SuppressWarnings("ConstantConditions")
    private void registerHandlers(@Nonnull final Catnip catnip, final int id) {
        if(handlersRegistered) {
            return;
        }
        handlersRegistered = true;
        
        catnip.on(DiscordEvent.READY, ready -> {
            logger.info("Logged in as {}#{}", ready.user().username(), ready.user().discriminator());
            logger.info("Trace: {}", ready.trace());
            client.updateMetadata("shard-id", SingyeongType.INTEGER, id);
        });
        // Push events to backend
        catnip.on(DiscordEvent.MESSAGE_CREATE, msg -> {
            // Only take default messages
            if(msg.type() == MessageType.DEFAULT) {
                // Only take the message if it has a guild attached
                if(msg.guildId() != null && msg.member() != null) {
                    @SuppressWarnings("ConstantConditions")
                    final var payload = new JsonObject()
                            .put("type", Raw.MESSAGE_CREATE)
                            .put("message", msg.toJson())
                            .put("guild", catnip.cache().guild(Objects.requireNonNull(msg.guildId())).toJson())
                            .put("user", msg.author().toJson())
                            .put("member", msg.member().toJson());
                    client.send("mewna-backend", new QueryBuilder().build(), payload);
                }
            }
        });
        catnip.on(DiscordEvent.GUILD_MEMBER_ADD, member -> {
            @SuppressWarnings("ConstantConditions")
            final var payload = new JsonObject()
                    .put("type", Raw.GUILD_MEMBER_ADD)
                    .put("guild", catnip.cache().guild(member.guildId()).toJson())
                    .put("user", catnip.cache().user(member.id()).toJson())
                    .put("member", member.toJson());
            client.send("mewna-backend", new QueryBuilder().build(), payload);
        });
        catnip.on(DiscordEvent.GUILD_MEMBER_REMOVE, member -> {
            @SuppressWarnings("ConstantConditions")
            final var payload = new JsonObject()
                    .put("type", Raw.GUILD_MEMBER_REMOVE)
                    .put("guild", catnip.cache().guild(member.guildId()).toJson())
                    .put("user", catnip.cache().user(member.id()).toJson())
                    .put("member", member.toJson());
            client.send("mewna-backend", new QueryBuilder().build(), payload);
        });
        // Voice
        catnip.on(DiscordEvent.VOICE_SERVER_UPDATE, vsu ->
                // Wait just in case the voice state update was delayed
                lighthouse.vertx().setTimer(100L, __ -> {
                    final VoiceState state = catnip.cache().voiceState(vsu.guildId(), catnip.selfUser().id());
                    if(state == null) {
                        return;
                    }
                    // TODO: Properly handle voice server failover for multiple nekomimi nodes
                    final JsonObject json = new JsonObject()
                            .put("type", "VOICE_JOIN")
                            .put("guild_id", vsu.guildId())
                            .put("session_id", state.sessionId())
                            .put("endpoint", vsu.endpoint())
                            .put("token", vsu.token());
                    // logger.info("Sending to nekomimi node:\n{}", json.encodePrettily());
                    client.send("nekomimi", new QueryBuilder().build(), json);
                }));
        // Update metadata
        catnip.on(DiscordEvent.GUILD_CREATE, __ -> updateGuildMetadata());
        catnip.on(DiscordEvent.GUILD_DELETE, __ -> updateGuildMetadata());
        catnip.on(DiscordEvent.GUILD_AVAILABLE, __ -> updateGuildMetadata());
        catnip.on(DiscordEvent.GUILD_UNAVAILABLE, __ -> updateGuildMetadata());
    }
    
    private void updateGuildMetadata() {
        client.updateMetadata("guilds", SingyeongType.LIST,
                new JsonArray(catnip.cache().guilds().stream()
                        .map(Snowflake::id)
                        .collect(Collectors.toList())));
        catnip.logAdapter().info("Updated {} guilds in metadata table.", catnip.cache().guilds().size());
    }
    
    @SuppressWarnings("WeakerAccess")
    public CompletableFuture<Collection<JsonObject>> cacheLookup(@Nonnull final JsonObject query) {
        return pubsub("cache", query);
    }
    
    @SuppressWarnings("WeakerAccess")
    public CompletableFuture<Collection<JsonObject>> pubsub(@Nonnull final String type, @Nonnull final JsonObject data) {
        return VertxCompletableFuture.from(lighthouse.vertx(), lighthouse.pubsub()
                .pubsub(new JsonObject().put("dist:type", type).put("d", data)));
    }
    
    private JsonObject handlePubsub(final JsonObject payload) {
        final String type = payload.getString("dist:type", null);
        final JsonObject data = payload.getJsonObject("d", null);
        if(type != null) {
            switch(type.toLowerCase()) {
                case "cache": {
                    final String mode = data.getString("mode", null);
                    final String id = data.getString("id", null);
                    final String guildId = data.getString("guild", null);
                    switch(mode) {
                        // Single lookups
                        case "channel": {
                            final Channel channel = catnip.cache().channel(guildId, id);
                            if(channel != null) {
                                return channel.toJson();
                            } else {
                                return new JsonObject();
                            }
                        }
                        case "guild": {
                            final Guild guild = catnip.cache().guild(id);
                            if(guild != null) {
                                return guild.toJson();
                            } else {
                                return new JsonObject();
                            }
                        }
                        case "role": {
                            final Role role = catnip.cache().role(guildId, id);
                            if(role != null) {
                                return role.toJson();
                            } else {
                                return new JsonObject();
                            }
                        }
                        case "user": {
                            final User user = catnip.cache().user(id);
                            if(user != null) {
                                return user.toJson();
                            } else {
                                return new JsonObject();
                            }
                        }
                        case "member": {
                            final Member member = catnip.cache().member(guildId, id);
                            if(member != null) {
                                return member.toJson();
                            } else {
                                return new JsonObject();
                            }
                        }
                        case "voice-state": {
                            final VoiceState voiceState = catnip.cache().voiceState(guildId, id);
                            if(voiceState != null) {
                                return voiceState.toJson();
                            } else {
                                return new JsonObject();
                            }
                        }
                        // Multiple lookups
                        case "channels": {
                            final List<Channel> channels = catnip.cache().channels(id);
                            return new JsonObject()
                                    .put("_type", "channels")
                                    .put("_data", channels.stream().map(Entity::toJson).collect(Collectors.toList()))
                                    ;
                        }
                        case "roles": {
                            final List<Role> roles = catnip.cache().roles(id);
                            return new JsonObject()
                                    .put("_type", "roles")
                                    .put("_data", roles.stream().map(Entity::toJson).collect(Collectors.toList()))
                                    ;
                        }
                        default: {
                            return new JsonObject();
                        }
                    }
                }
                default: {
                    return new JsonObject();
                }
            }
        } else {
            return new JsonObject();
        }
    }
}
