package com.mewna.discord.shard;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.entity.channel.GuildChannel;
import com.mewna.catnip.entity.user.User;
import com.mewna.catnip.rest.handler.RestChannel;
import com.mewna.yangmal.Command;
import com.mewna.yangmal.context.Context;

import java.lang.management.ManagementFactory;

import static com.mewna.discord.shard.ContextParams.CHANNEL;

/**
 * @author amy
 * @since 1/18/19.
 */
@SuppressWarnings({"ConstantConditions", "unused"})
public class Commands {
    @Command(names = {"ram", "mem"}, description = "")
    public void ram(final Context ctx) {
        final var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final var nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        final long heapUsed = heap.getUsed() / (1024L * 1024L);
        final long heapAllocated = heap.getCommitted() / (1024L * 1024L);
        final long heapTotal = heap.getMax() / (1024L * 1024L);
        final long heapInit = heap.getInit() / (1024L * 1024L);
        final long nonHeapUsed = nonHeap.getUsed() / (1024L * 1024L);
        final long nonHeapAllocated = nonHeap.getCommitted() / (1024L * 1024L);
        final long nonHeapTotal = nonHeap.getMax() / (1024L * 1024L);
        final long nonHeapInit = nonHeap.getInit() / (1024L * 1024L);
        
        final var out = "RAM:\n" +
                "```CSS\n" +
                "[HEAP]\n" +
                "     [Init] " + heapInit + "MB\n" +
                "     [Used] " + heapUsed + "MB\n" +
                "    [Alloc] " + heapAllocated + "MB\n" +
                "    [Total] " + heapTotal + "MB\n" +
                "[NONHEAP]\n" +
                "     [Init] " + nonHeapInit + "MB\n" +
                "     [Used] " + nonHeapUsed + "MB\n" +
                "    [Alloc] " + nonHeapAllocated + "MB\n" +
                "    [Total] " + nonHeapTotal + "MB\n" +
                "```";
        ctx.service(RestChannel.class).ifPresent(c -> c.sendMessage(ctx.param(CHANNEL), out));
    }
    
    @Command(names = "cachecheck", description = "")
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public void meme(final Context ctx) {
        final var catnip = ctx.service(Catnip.class).get();
        final var rest = ctx.service(RestChannel.class).get();
        final User user = catnip.cache().user("128316294742147072");
        rest.sendMessage(ctx.param(CHANNEL), "```Javascript\n" + user.toJson().encodePrettily() + "\n```");
        final GuildChannel channel = catnip.cache().channel("267500017260953601", "435086116433952768");
        rest.sendMessage(ctx.param(CHANNEL), "```Javascript\n" + channel.toJson().encodePrettily() + "\n```");
    }
}
