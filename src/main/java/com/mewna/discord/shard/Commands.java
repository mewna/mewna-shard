package com.mewna.discord.shard;

import com.mewna.catnip.Catnip;
import com.mewna.catnip.entity.guild.Guild;
import com.mewna.catnip.rest.handler.RestChannel;
import com.mewna.yangmal.Command;
import com.mewna.yangmal.context.Context;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;

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
    
    @Command(names = "stats", description = "")
    public void stats(final Context ctx) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        final var catnip = ctx.service(Catnip.class).get();
        final var out = "Stats:\n" +
                "```CSS\n" +
                "  [guilds] " + catnip.cache().guilds().size() + '\n' +
                "   [users] " + catnip.cache().users().size() + '\n' +
                " [members] " + catnip.cache().members().size() + '\n' +
                "[channels] " + catnip.cache().channels().size() + '\n' +
                "   [roles] " + catnip.cache().roles().size() + '\n' +
                "```";
        ctx.service(RestChannel.class).ifPresent(c -> c.sendMessage(ctx.param(CHANNEL), out));
    }
    
    @Command(names = "largest", description = "")
    public void largest(final Context ctx) {
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        final var catnip = ctx.service(Catnip.class).get();
        final List<Guild> guilds = catnip.cache().guilds().stream()
                .sorted((g, h) -> Long.compare(h.memberCount(), g.memberCount()))
                .limit(10).collect(Collectors.toList());
        final StringBuilder sb = new StringBuilder("__Largest guilds__:\n```CSS\n");
        for(final Guild g : guilds) {
            sb.append('[').append(g.name()).append("] ").append(g.memberCount()).append('\n');
        }
        sb.append("```");
        ctx.service(RestChannel.class).ifPresent(c -> c.sendMessage(ctx.param(CHANNEL), sb.toString()));
    }
}
