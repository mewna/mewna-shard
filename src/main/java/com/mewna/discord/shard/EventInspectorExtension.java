package com.mewna.discord.shard;

import com.mewna.catnip.extension.AbstractExtension;
import com.mewna.catnip.extension.hook.CatnipHook;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;

/**
 * @author amy
 * @since 12/13/18.
 */
class EventInspectorExtension extends AbstractExtension {
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
                mewnaShard.statsClient().gauge("gatewayEvents", 0, "type:" + type);
                return json;
            }
        });
    }
}
