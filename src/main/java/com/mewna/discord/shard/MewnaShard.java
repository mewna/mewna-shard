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
import com.timgroup.statsd.NoOpStatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import gg.amy.singyeong.Dispatch;
import gg.amy.singyeong.QueryBuilder;
import gg.amy.singyeong.SingyeongClient;
import gg.amy.singyeong.SingyeongType;
import io.sentry.Sentry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
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
    private final StatsDClient statsClient;
    private final Vertx vertx = Vertx.vertx();
    private SingyeongClient client;
    private Catnip catnip;
    private boolean handlersRegistered;
    
    private MewnaShard() {
        if(System.getenv("STATSD_ENABLED") != null) {
            statsClient = new NonBlockingStatsDClient("v2.shards", System.getenv("STATSD_HOST"), 8125);
        } else {
            statsClient = new NoOpStatsDClient();
        }
    }
    
    public static void main(final String[] args) {
        new MewnaShard().start();
    }
    
    private void start() {
        logger.info("Starting Mewna shard...");
        if(System.getenv("SENTRY_DSN") != null) {
            Sentry.init(System.getenv("SENTRY_DSN"));
        }
        final int shardCount = Integer.parseInt(System.getenv("SHARD_COUNT"));
        if(shardCount <= 0) {
            throw new IllegalStateException("shard count " + shardCount + " <= 0!!!");
        }
        final String redisHost = System.getenv("REDIS_HOST");
        final String redisAuth = System.getenv("REDIS_AUTH");
        final int healthPort = Integer.parseInt(Optional.ofNullable(System.getenv("PORT"))
                .orElseGet(() -> "" + (9000 + new Random().nextInt(1000))));
        logger.info("Running healthcheck on port {}...", healthPort);
        client = SingyeongClient.create(vertx, System.getenv("SINGYEONG_DSN"));
        client.connect()
                .thenAccept(__ -> {
                    client.onEvent(this::handleSingyeongDispatch);
                    client.onInvalid(invalid -> {
                        logger.warn("Got invalid:");
                        logger.warn(invalid.reason());
                    });
                    vertx.executeBlocking(future -> {
                        catnip = Catnip.catnip(new CatnipOptions(System.getenv("TOKEN"))
                                        .cacheWorker(new ClearableCache())
                                        .cacheFlags(EnumSet.of(CacheFlag.DROP_EMOJI, CacheFlag.DROP_GAME_STATUSES)),
                                vertx);
                        registerHandlers(catnip);
                        catnip.startShards();
                        future.complete(null);
                    }, res -> {
                    });
                })
                .exceptionally(e -> {
                    logger.error("Couldn't connect to singyeong", e);
                    System.exit(0);
                    return null;
                });
    }
    
    private int guild2shard(final String guild) {
        return (int) ((Long.parseLong(guild) >> 22) % catnip.shardManager().shardCount());
    }
    
    private void handleSingyeongDispatch(final Dispatch dispatch) {
        final JsonObject d = dispatch.data();
        final String nonce = dispatch.nonce();
        if(d.containsKey("type")) {
            switch(d.getString("type").toUpperCase()) {
                case "VOICE_JOIN": {
                    vertx.eventBus().send(CatnipShard.websocketMessageQueueAddress(guild2shard(d.getString("guild_id"))),
                            CatnipShard.basePayload(GatewayOp.VOICE_STATE_UPDATE, new JsonObject()
                                    .put("guild_id", d.getString("guild_id"))
                                    .put("channel_id", d.getString("channel_id"))
                                    .put("self_deaf", true)
                                    .put("self_mute", false)));
                    break;
                }
                case "VOICE_LEAVE": {
                    //noinspection ConstantConditions
                    final JsonObject json = new JsonObject()
                            .put("type", "VOICE_LEAVE")
                            .put("guild_id", d.getString("guild_id"));
                    //logger.info("Sending to nekomimi node:\n{}", json.encodePrettily());
                    client.send("nekomimi", new QueryBuilder().build(), json);
                    vertx.eventBus().send(CatnipShard.websocketMessageQueueAddress(guild2shard(d.getString("guild_id"))),
                            CatnipShard.basePayload(GatewayOp.VOICE_STATE_UPDATE, new JsonObject()
                                    .put("guild_id", d.getString("guild_id"))
                                    .putNull("channel_id")
                                    .put("self_deaf", true)
                                    .put("self_mute", false)));
                    break;
                }
                case "CACHE": {
                    final JsonObject query = d.getJsonObject("query");
                    final String mode = query.getString("mode", null);
                    
                    final JsonObject res;
                    {
                        final String id = query.getString("id", null);
                        final String guildId = query.getString("guild", null);
                        switch(mode) {
                            // Single lookups
                            case "channel": {
                                final Channel channel = catnip.cache().channel(guildId, id);
                                if(channel != null) {
                                    res = channel.toJson();
                                } else {
                                    res = new JsonObject();
                                }
                                break;
                            }
                            case "guild": {
                                final Guild guild = catnip.cache().guild(id);
                                if(guild != null) {
                                    res = guild.toJson();
                                } else {
                                    res = new JsonObject();
                                }
                                break;
                            }
                            case "role": {
                                final Role role = catnip.cache().role(guildId, id);
                                if(role != null) {
                                    res = role.toJson();
                                } else {
                                    res = new JsonObject();
                                }
                                break;
                            }
                            case "user": {
                                final User user = catnip.cache().user(id);
                                if(user != null) {
                                    res = user.toJson();
                                } else {
                                    res = new JsonObject();
                                }
                                break;
                            }
                            case "member": {
                                final Member member = catnip.cache().member(guildId, id);
                                if(member != null) {
                                    res = member.toJson();
                                } else {
                                    res = new JsonObject();
                                }
                                break;
                            }
                            case "voice-state": {
                                final VoiceState voiceState = catnip.cache().voiceState(guildId, id);
                                if(voiceState != null) {
                                    res = voiceState.toJson();
                                } else {
                                    res = new JsonObject();
                                }
                                break;
                            }
                            // Multiple lookups
                            case "channels": {
                                final List<Channel> channels = catnip.cache().channels(id);
                                res = new JsonObject()
                                        .put("_type", "channels")
                                        .put("_data", channels.stream().map(Entity::toJson).collect(Collectors.toList()))
                                ;
                                break;
                            }
                            case "roles": {
                                final List<Role> roles = catnip.cache().roles(id);
                                res = new JsonObject()
                                        .put("_type", "roles")
                                        .put("_data", roles.stream().map(Entity::toJson).collect(Collectors.toList()))
                                ;
                                break;
                            }
                            default: {
                                res = new JsonObject();
                                break;
                            }
                        }
                    }
                    // Since routing is effectively random, broadcast to maximize chance of the
                    // sender hearing it
                    client.broadcast("mewna-backend", nonce, new QueryBuilder().build(), res);
                    break;
                }
            }
        }
    }
    
    @SuppressWarnings("ConstantConditions")
    private void registerHandlers(@Nonnull final Catnip catnip) {
        if(handlersRegistered) {
            return;
        }
        handlersRegistered = true;
        
        //noinspection CodeBlock2Expr
        catnip.vertx().setPeriodic(15000L, __ -> {
            for(int id = 0; id < catnip.shardManager().shardCount(); id++) {
                final int finalId = id;
                catnip.shardManager().isConnected(id).thenAccept(b -> {
                    if(finalId > -1 && b) {
                        statsClient.gauge("members", catnip.cache().members().size(), "shard:" + finalId);
                        statsClient.gauge("users", catnip.cache().users().size(), "shard:" + finalId);
                        statsClient.gauge("guilds", catnip.cache().guilds().size(), "shard:" + finalId);
                        statsClient.gauge("roles", catnip.cache().roles().size(), "shard:" + finalId);
                        statsClient.gauge("presences", catnip.cache().presences().size(), "shard:" + finalId);
                        statsClient.gauge("channels", catnip.cache().channels().size(), "shard:" + finalId);
                        statsClient.gauge("emojis", catnip.cache().emojis().size(), "shard:" + finalId);
                        statsClient.gauge("voiceStates", catnip.cache().voiceStates().size(), "shard:" + finalId);
                    }
                });
            }
        });
        
        catnip.loadExtension(new EventInspectorExtension(this))
                .loadExtension(new InternalCommandExtension(this));
        
        catnip.on(DiscordEvent.READY, ready -> {
            logger.info("Logged in as {}#{}", ready.user().username(), ready.user().discriminator());
            logger.info("Trace: {}", ready.trace());
            logger.info("Received {} unavailable guilds.", ready.guilds().size());
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
            statsClient.gauge("members", catnip.cache().members().size());
            statsClient.gauge("users", catnip.cache().users().size());
        });
        catnip.on(DiscordEvent.GUILD_MEMBER_REMOVE, member -> {
            @SuppressWarnings("ConstantConditions")
            final var payload = new JsonObject()
                    .put("type", Raw.GUILD_MEMBER_REMOVE)
                    .put("guild", catnip.cache().guild(member.guildId()).toJson())
                    .put("user", catnip.cache().user(member.id()).toJson())
                    .put("member", member.toJson());
            client.send("mewna-backend", new QueryBuilder().build(), payload);
            statsClient.gauge("members", catnip.cache().members().size());
            statsClient.gauge("users", catnip.cache().users().size());
        });
        // Voice
        catnip.on(DiscordEvent.VOICE_SERVER_UPDATE, vsu ->
                // Wait just in case the voice state update was delayed
                vertx.setTimer(100L, __ -> {
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
        statsClient.gauge("guilds", guildIds.size());
        client.updateMetadata("guilds", SingyeongType.LIST,
                new JsonArray(guildIds));
        catnip.logAdapter().debug("Updated {} guilds in metadata table.", guildIds.size());
    }
}
