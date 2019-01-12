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
            public JsonObject rawGatewayReceiveHook(@Nonnull final JsonObject json) {
                final String type = json.getString("t");
                if(type != null) {
                    mewnaShard.statsClient().count("gatewayEvents", 1, "type:" + type);
                }
                return json;
            }
        });
    }
}
