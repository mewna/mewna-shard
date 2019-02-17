package com.mewna.discord.shard;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.channel.GuildChannel;
import com.mewna.catnip.entity.guild.Guild;
import com.mewna.catnip.entity.user.User;
import gg.amy.catnip.utilities.menu.MenuExtension;
import gg.amy.catnip.utilities.typesafeCommands.Command;
import gg.amy.catnip.utilities.typesafeCommands.Context;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author amy
 * @since 1/18/19.
 */
public class Commands {
    @Command(names = {"ram", "mem"})
    public void ram(final Context ctx) {
        final MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        final MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        final long heapUsed = heap.getUsed() / (1024L * 1024L);
        final long heapAllocated = heap.getCommitted() / (1024L * 1024L);
        final long heapTotal = heap.getMax() / (1024L * 1024L);
        final long heapInit = heap.getInit() / (1024L * 1024L);
        final long nonHeapUsed = nonHeap.getUsed() / (1024L * 1024L);
        final long nonHeapAllocated = nonHeap.getCommitted() / (1024L * 1024L);
        final long nonHeapTotal = nonHeap.getMax() / (1024L * 1024L);
        final long nonHeapInit = nonHeap.getInit() / (1024L * 1024L);
        
        ctx.sendMessage("RAM:\n" +
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
                "```");
    }
    
    @Command(names = "stats")
    public void stats(final Context ctx) {
        ctx.sendMessage("Stats:\n" +
                "```CSS\n" +
                "  [guilds] " + ctx.catnip().cache().guilds().size() + '\n' +
                "   [users] " + ctx.catnip().cache().users().size() + '\n' +
                " [members] " + ctx.catnip().cache().members().size() + '\n' +
                "[channels] " + ctx.catnip().cache().channels().size() + '\n' +
                "   [roles] " + ctx.catnip().cache().roles().size() + '\n' +
                "```");
    }
    
    @Command(names = "lookup")
    public void lookup(final Context ctx, final User user, final Guild guild, final Guild roles,
                       final Channel channel, final Guild channels) {
        if(exactlyOne(user, guild, roles, channel, channels)) {
            final List<String> data = new ArrayList<>();
            if(user != null) {
                data.add(String.format("%s#%s (%s)", user.username(), user.discriminator(), user.id()));
            }
            if(guild != null) {
                data.add(String.format("%s (%s) in %s", guild.name(), guild.id(), guild.region()));
            }
            if(channel != null && channel.isGuild()) {
                final GuildChannel gc = channel.asGuildChannel();
                data.add(String.format("#%s (%s - %s)", gc.name(), gc.guildId(), gc.id()));
            }
            if(roles != null) {
                roles.roles().forEach(r -> {
                    data.add(String.format("%s 0x%s (%s - %s)", r.name(), Integer.toHexString(r.color()), r.guildId(), r.id()));
                });
            }
            if(channels != null) {
                channels.channels().forEach(c -> {
                    final GuildChannel gc = c.asGuildChannel();
                    data.add(String.format("#%s (%s - %s)", gc.name(), gc.guildId(), gc.id()));
                });
            }
            
            final List<String> pages = Lists.partition(data, 10).stream()
                    .map(e -> "```\n" + String.join("\n", e) + "\n```")
                    .collect(Collectors.toList());
            
            //noinspection ConstantConditions
            ctx.catnip().extensionManager().extension(MenuExtension.class)
                    .createPaginatedMenu("Search results:", ImmutableList.copyOf(pages))
                    .accept(ctx.source().author(), ctx.source().channelId());
        }
    }
    
    @Command(names = "largest")
    public void largest(final Context ctx) {
        final List<Guild> guilds = ctx.catnip().cache().guilds().stream()
                .sorted((g, h) -> Long.compare(h.memberCount(), g.memberCount()))
                .limit(10).collect(Collectors.toList());
        final StringBuilder sb = new StringBuilder("__Largest guilds__:\n```CSS\n");
        for(final Guild g : guilds) {
            sb.append('[').append(g.name()).append("] ").append(g.memberCount()).append('\n');
        }
        sb.append("```");
        ctx.sendMessage(sb.toString());
    }
    
    private boolean exactlyOne(final Object... objects) {
        int nonnull = 0;
        for(final Object o : objects) {
            if(o != null) {
                ++nonnull;
            }
        }
        return nonnull == 1;
    }
}
