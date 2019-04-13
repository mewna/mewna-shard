package com.mewna.discord.shard;

import javax.annotation.Nonnull;
import java.net.Inet4Address;
import java.net.UnknownHostException;

/**
 * @author amy
 * @since 4/11/19.
 */
public final class Utils {
    private Utils() {
    }
    
    @Nonnull
    public static String ip() {
        final String podIpEnv = System.getenv("POD_IP");
        if(podIpEnv != null) {
            return podIpEnv;
        } else {
            try {
                return Inet4Address.getLocalHost().getHostAddress();
            } catch(final UnknownHostException var3) {
                throw new IllegalStateException("DNS broken? Can't resolve localhost!", var3);
            }
        }
    }
}
