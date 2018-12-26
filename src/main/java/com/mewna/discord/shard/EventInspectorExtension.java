package com.mewna.discord.shard;

import com.mewna.catnip.extension.AbstractExtension;
import com.mewna.catnip.extension.hook.CatnipHook;
import com.mewna.catnip.shard.GatewayOp;
import io.sentry.Sentry;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * @author amy
 * @since 12/13/18.
 */
class EventInspectorExtension extends AbstractExtension {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private MewnaShard mewnaShard;
    
    public EventInspectorExtension(final MewnaShard mewnaShard) {
        super("event-inspector");
        this.mewnaShard = mewnaShard;
    }
    
    @Override
    public void start() throws Exception {
        registerHook(new CatnipHook() {
            @Override
            public JsonObject rawGatewaySendHook(@Nonnull final JsonObject json) {
                try {
                    if(json.getInteger("op") == GatewayOp.VOICE_STATE_UPDATE.opcode()) {
                        logger.info("Starting voice join for guild {} via gateway", json.getJsonObject("d").getString("guild_id"));
                    }
                } catch(final Exception e) {
                    Sentry.capture(e);
                }
                return json;
            }
    
            @Override
            public JsonObject rawGatewayReceiveHook(@Nonnull final JsonObject json) {
                final String type = json.getString("t");
                mewnaShard.statsClient().count("gatewayEvents", 1, "type:" + type);
                return json;
            }
        });
    }
}
