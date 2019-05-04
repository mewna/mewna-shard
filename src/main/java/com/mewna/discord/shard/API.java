package com.mewna.discord.shard;

import com.mewna.catnip.entity.Entity;
import com.mewna.catnip.entity.channel.TextChannel;
import com.mewna.catnip.entity.channel.VoiceChannel;
import com.mewna.catnip.entity.guild.Guild;
import com.mewna.catnip.entity.guild.Member;
import com.mewna.catnip.entity.guild.Role;
import com.mewna.catnip.entity.user.User;
import com.mewna.catnip.entity.user.VoiceState;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author amy
 * @since 4/11/19.
 */
class API {
    private final Vertx vertx = Vertx.vertx();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Mewna mewna;
    private final int shardCount;
    
    API(final Mewna mewna, final int count) {
        this.mewna = mewna;
        shardCount = count;
    }
    
    private int shardIdFor(@Nonnull final String guildId) {
        final long idLong = Long.parseUnsignedLong(guildId);
        return (int) ((idLong >>> 22) % shardCount);
    }
    
    @SuppressWarnings("ConstantConditions")
    void start() {
        logger.info("Starting API server...");
        final HttpServer server = vertx.createHttpServer();
        final Router router = Router.router(vertx);
        
        router.get("/cache/guild/:id").handler(ctx -> {
            try {
                final String id = ctx.pathParam("id");
                final Guild guild = mewna.catnips().get(shardIdFor(id)).cache().guild(id);
                if(guild != null) {
                    ctx.response().end(guild.toJson().encode());
                } else {
                    ctx.response().setStatusCode(404).end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        router.get("/cache/user/:id").handler(ctx -> {
            try {
                final String id = ctx.pathParam("id");
                final User user = mewna.catnips().get(shardIdFor(id)).cache().user(id);
                if(user != null) {
                    ctx.response().end(user.toJson().encode());
                } else {
                    ctx.response().setStatusCode(404).end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        router.get("/cache/guild/:guild/channels/text").handler(ctx -> {
            try {
                final String id = ctx.pathParam("guild");
                
                final List<JsonObject> collect = mewna.catnips().get(shardIdFor(id))
                        .cache()
                        .channels(id)
                        .stream()
                        .filter(e -> e instanceof TextChannel)
                        .map(Entity::toJson)
                        .collect(Collectors.toList());
                
                ctx.response().end(new JsonArray(collect).encode());
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonArray().encode());
            }
        });
        
        router.get("/cache/guild/:guild/channels/text/:id").handler(ctx -> {
            try {
                final String guild = ctx.pathParam("guild");
                final String id = ctx.pathParam("id");
                final TextChannel channel = mewna.catnips().get(shardIdFor(guild)).cache().channel(guild, id).asTextChannel();
                if(channel != null) {
                    ctx.response().end(channel.toJson().encode());
                } else {
                    ctx.response().end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        router.get("/cache/guild/:guild/channels/voice/:id").handler(ctx -> {
            try {
                final String guild = ctx.pathParam("guild");
                final String id = ctx.pathParam("id");
                final VoiceChannel channel = mewna.catnips().get(shardIdFor(guild)).cache().channel(guild, id).asVoiceChannel();
                if(channel != null) {
                    ctx.response().end(channel.toJson().encode());
                } else {
                    ctx.response().end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        router.get("/cache/guild/:guild/roles").handler(ctx -> {
            try {
                final String id = ctx.pathParam("guild");
                
                final List<JsonObject> collect = mewna.catnips().get(shardIdFor(id))
                        .cache()
                        .roles(id)
                        .stream()
                        .map(Entity::toJson)
                        .collect(Collectors.toList());
                
                ctx.response().end(new JsonArray(collect).encode());
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonArray().encode());
            }
        });
        
        router.get("/cache/guild/:guild/roles/:id").handler(ctx -> {
            try {
                final String guild = ctx.pathParam("guild");
                final String id = ctx.pathParam("id");
                final Role role = mewna.catnips().get(shardIdFor(guild)).cache().role(guild, id);
                if(role != null) {
                    ctx.response().end(role.toJson().encode());
                } else {
                    ctx.response().end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        router.get("/cache/guild/:guild/member/:id").handler(ctx -> {
            try {
                final String guild = ctx.pathParam("guild");
                final String id = ctx.pathParam("id");
                final Member member = mewna.catnips().get(shardIdFor(guild)).cache().member(guild, id);
                if(member != null) {
                    ctx.response().end(member.toJson().encode());
                } else {
                    ctx.response().end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        router.get("/cache/guild/:guild/member/:id/voiceState").handler(ctx -> {
            try {
                final String guild = ctx.pathParam("guild");
                final String id = ctx.pathParam("id");
                final VoiceState voiceState = mewna.catnips().get(shardIdFor(guild)).cache().voiceState(guild, id);
                if(voiceState != null) {
                    ctx.response().end(voiceState.toJson().encode());
                } else {
                    ctx.response().end(new JsonObject().encode());
                }
            } catch(final NullPointerException e) {
                ctx.response().setStatusCode(404).end(new JsonObject().encode());
            }
        });
        
        server.requestHandler(router).listen(mewna.port());
    }
}
