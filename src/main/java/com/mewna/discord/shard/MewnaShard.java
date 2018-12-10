package com.mewna.discord.shard;

import com.google.common.collect.ImmutableList;
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
import com.mewna.catnip.shard.CatnipShard.ShardConnectState;
import com.mewna.catnip.shard.DiscordEvent;
import com.mewna.catnip.shard.DiscordEvent.Raw;
import com.mewna.catnip.shard.GatewayOp;
import com.mewna.catnip.shard.manager.DefaultShardManager;
import com.mewna.catnip.shard.manager.ShardCondition;
import com.mewna.lighthouse.Lighthouse;
import gg.amy.singyeong.Dispatch;
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
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
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
    private static final String INTERNAL_PREFIX = "amyware!";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @Getter
    private Lighthouse lighthouse;
    private SingyeongClient client;
    private Catnip catnip;
    private boolean handlersRegistered;
    private int lastShardId = -1;
    
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
                (a, b) -> null, this::handlePubsub);
        client = SingyeongClient.create(lighthouse.vertx(), System.getenv("SINGYEONG_DSN"));
        client.connect()
                .thenAccept(__ -> {
                    client.onEvent(this::handleSingyeongDispatch);
                    client.onInvalid(invalid -> {
                        logger.warn("Got invalid:");
                        logger.warn(invalid.reason());
                    });
                    lighthouse.init().setHandler(res -> {
                        if(res.succeeded()) {
                            logger.info("Started lighthouse!");
                            logger.info("Awaiting clustering...");
                            lighthouse.vertx().setTimer(1000L, ___ -> startSharding());
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
    
    private void handleSingyeongDispatch(final Dispatch dispatch) {
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
                    final String mode = query.getString("mode", null);
                    final CompletableFuture<Collection<JsonObject>> collectionFuture = cacheLookup(query);
                    collectionFuture.thenAccept(res -> {
                        final JsonObject cacheResult;
                        if(mode.equalsIgnoreCase("channels") || mode.equalsIgnoreCase("roles")) {
                            cacheResult = res.stream()
                                    .filter(e -> !e.isEmpty())
                                    .filter(e -> !e.getJsonArray("_data").isEmpty())
                                    .findFirst()
                                    .orElse(new JsonObject().put("_type", mode).put("_data", new JsonArray()));
                        } else {
                            cacheResult = res.stream()
                                    .filter(e -> !e.isEmpty())
                                    .findFirst()
                                    .orElse(new JsonObject());
                        }
                        // Since routing is effectively random, broadcast to maximize chance of the
                        // sender hearing it
                        client.broadcast("mewna-backend", nonce, new QueryBuilder().build(), cacheResult);
                    });
                    break;
                }
            }
        }
    }
    
    private void startSharding() {
        c(lighthouse.service().lock())
                .thenAccept(lock -> {
                    if(lock) {
                        c(lighthouse.service().getKnownShards())
                                .thenAccept(ids -> {
                                    final List<Integer> all = new ArrayList<>(lighthouse.service().getAllShards());
                                    all.removeAll(ids);
                                    if(!all.isEmpty()) {
                                        lighthouse.service().shardId(all.get(0));
                                        handleSharding(all.get(0), lighthouse.service().getAllShards().size());
                                        lighthouse.service().unlock();
                                        catnip.startShards();
                                    } else {
                                        logger.error("Too many shards!!!");
                                        lighthouse.service().unlock();
                                        scheduleSharding();
                                    }
                                })
                                .exceptionally(e -> {
                                    logger.info("Shard id collection failed, rescheduling...", e);
                                    lighthouse.service().unlock();
                                    scheduleSharding();
                                    return null;
                                });
                    } else {
                        scheduleSharding();
                    }
                })
                .exceptionally(e -> {
                    logger.warn("Locking failed, rescheduling...", e);
                    scheduleSharding();
                    return null;
                });
    }
    
    private void scheduleSharding() {
        lighthouse.vertx().setTimer(500L, __ -> startSharding());
    }
    
    private <T> CompletableFuture<T> c(@Nonnull final Future<T> f) {
        return VertxCompletableFuture.from(lighthouse.vertx(), f);
    }
    
    private void handleSharding(final int id, final int limit) {
        logger.info("Received shard id {} / {}", id, limit);
        lastShardId = id;
        
        // Don't initialize multiple times
        if(catnip == null) {
            catnip = Catnip.catnip(new CatnipOptions(System.getenv("TOKEN"))
                            .shardManager(new DefaultShardManager(limit, ImmutableList.of(id))
                                    .addCondition(new DistributedShardingCondition()))
                            .cacheWorker(new ClearableCache())
                            .cacheFlags(EnumSet.of(CacheFlag.DROP_EMOJI, CacheFlag.DROP_GAME_STATUSES)),
                    lighthouse.vertx());
            registerHandlers(catnip, id);
        }
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
            logger.info("Received {} unavailable guilds.", ready.guilds().size());
        });
        // Push events to backend
        catnip.on(DiscordEvent.MESSAGE_CREATE, msg -> {
            // Only take default messages
            if(msg.type() == MessageType.DEFAULT) {
                // Only take the message if it has a guild attached
                if(msg.guildId() != null && msg.member() != null) {
                    if(msg.author().id().equalsIgnoreCase("128316294742147072")
                            && msg.content().startsWith(INTERNAL_PREFIX)) {
                        final var c = msg.content().substring(INTERNAL_PREFIX.length());
                        switch(c.toLowerCase()) {
                            case "stats": {
                                pubsub("stats", new JsonObject())
                                        .thenAccept(objs -> {
                                            int guilds = 0;
                                            int users = 0;
                                            int members = 0;
                                            int channels = 0;
                                            int roles = 0;
                                            for(final JsonObject obj : objs) {
                                                guilds += obj.getInteger("guilds");
                                                users += obj.getInteger("users");
                                                members += obj.getInteger("members");
                                                channels += obj.getInteger("channels");
                                                roles += obj.getInteger("roles");
                                            }
                                            msg.channel().sendMessage("Stats:\n" +
                                                    "```CSS\n" +
                                                    "  [guilds] " + guilds + '\n' +
                                                    "   [users] " + users + '\n' +
                                                    " [members] " + members + '\n' +
                                                    "[channels] " + channels + '\n' +
                                                    "   [roles] " + roles + '\n' +
                                                    "```");
                                        });
                                break;
                            }
                            case "here": {
                                msg.channel().sendMessage("Stats:\n" +
                                        "```CSS\n" +
                                        "   [guild] " + msg.guildId() + '\n' +
                                        "   [users] " + catnip.cache().users().size() + '\n' +
                                        " [members] " + msg.guild().memberCount() + '\n' +
                                        "[channels] " + msg.guild().channels().size() + '\n' +
                                        "   [roles] " + msg.guild().roles().size() + '\n' +
                                        "```");
                                break;
                            }
                            case "ram": {
                                pubsub("ram", new JsonObject()).thenAccept(objs -> {
                                    int heapUsed = 0;
                                    int heapAllocated = 0;
                                    int heapTotal = 0;
                                    int heapInit = 0;
                                    int nonHeapUsed = 0;
                                    int nonHeapAllocated = 0;
                                    int nonHeapTotal = 0;
                                    int nonHeapInit = 0;
    
                                    for(final JsonObject o : objs) {
                                        final JsonObject heap = o.getJsonObject("heap");
                                        final JsonObject nonHeap = o.getJsonObject("nonheap");
                                        heapUsed += heap.getInteger("heapUsed");
                                        heapAllocated += heap.getInteger("heapAllocated");
                                        heapTotal += heap.getInteger("heapTotal");
                                        heapInit += heap.getInteger("heapInit");
                                        nonHeapUsed += nonHeap.getInteger("nonHeapUsed");
                                        nonHeapAllocated += nonHeap.getInteger("nonHeapAllocated");
                                        nonHeapTotal += nonHeap.getInteger("nonHeapTotal");
                                        nonHeapInit += nonHeap.getInteger("nonHeapInit");
                                    }
    
                                    heapUsed /= 1024 * 1024;
                                    heapAllocated /= 1024 * 1024;
                                    heapTotal /= 1024 * 1024;
                                    heapInit /= 1024 * 1024;
                                    nonHeapUsed /= 1024 * 1024;
                                    nonHeapAllocated /= 1024 * 1024;
                                    nonHeapTotal /= 1024 * 1024;
                                    nonHeapInit /= 1024 * 1024;
                                    
                                    msg.channel().sendMessage("RAM:\n" +
                                            "```CSS\n" +
                                            "[HEAP]\n" +
                                            "     [Init] " + heapInit + '\n' +
                                            "     [Used] " + heapUsed + '\n' +
                                            "    [Alloc] " + heapAllocated + '\n' +
                                            "    [Total] " + heapTotal + '\n' +
                                            "[NONHEAP]\n" +
                                            "     [Init] " + nonHeapInit + '\n' +
                                            "     [Used] " + nonHeapUsed + '\n' +
                                            "    [Alloc] " + nonHeapAllocated + '\n' +
                                            "    [Total] " + nonHeapTotal + '\n' +
                                            "```");
                                });
                                break;
                            }
                            default: {
                                msg.channel().sendMessage("what is " + c);
                            }
                        }
                    } else {
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
        catnip.on(DiscordEvent.GUILD_CREATE, e -> updateGuildMetadata(Raw.GUILD_CREATE, e.id()));
        catnip.on(DiscordEvent.GUILD_DELETE, e -> updateGuildMetadata(Raw.GUILD_DELETE, e.id()));
        catnip.on(DiscordEvent.GUILD_AVAILABLE, e -> updateGuildMetadata(Raw.GUILD_AVAILABLE, e.id()));
        catnip.on(DiscordEvent.GUILD_UNAVAILABLE, e -> updateGuildMetadata(Raw.GUILD_UNAVAILABLE, e.id()));
    }
    
    private void updateGuildMetadata(final String event, final String id) {
        updateGuildMetadata(catnip.cache().guilds().stream().map(Snowflake::id).collect(Collectors.toList()));
    }
    
    private void updateGuildMetadata(final List<String> guildIds) {
        client.updateMetadata("guilds", SingyeongType.LIST,
                new JsonArray(guildIds));
        catnip.logAdapter().info("Updated {} guilds in metadata table.", guildIds.size());
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
                case "ram": {
                    final MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                    final MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
                    return new JsonObject()
                            .put("heap", new JsonObject()
                                    .put("used", heap.getUsed())
                                    .put("allocated", heap.getCommitted())
                                    .put("total", heap.getMax())
                                    .put("init", heap.getInit())
                            )
                            .put("nonheap", new JsonObject()
                                    .put("used", nonHeap.getUsed())
                                    .put("allocated", nonHeap.getCommitted())
                                    .put("total", nonHeap.getMax())
                                    .put("init", nonHeap.getInit())
                            );
                }
                case "stats": {
                    return new JsonObject()
                            .put("guilds", catnip.cache().guilds().size())
                            .put("channels", catnip.cache().channels().size())
                            .put("roles", catnip.cache().roles().size())
                            .put("users", catnip.cache().users().size())
                            .put("members", catnip.cache().members().size())
                            ;
                }
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
    
    @Accessors(fluent = true)
    private final class DistributedShardingCondition implements ShardCondition {
        @Override
        public CompletableFuture<Boolean> preshard() {
            final Future<Boolean> future = Future.future();
            lighthouse().service().lock().setHandler(res -> {
                if(res.succeeded() && res.result()) {
                    future.complete(true);
                } else {
                    future.complete(false);
                }
            });
            return VertxCompletableFuture.from(lighthouse().vertx(), future);
        }
        
        @Override
        public void postshard(@Nonnull final ShardConnectState shardConnectState) {
            switch(shardConnectState) {
                case READY: {
                    lighthouse().service().unlock();
                    break;
                }
                case FAILED: {
                    lighthouse().service().unlock();
                    break;
                }
                case RESUMED: {
                    lighthouse().service().unlock();
                    break;
                }
            }
        }
    }
}
