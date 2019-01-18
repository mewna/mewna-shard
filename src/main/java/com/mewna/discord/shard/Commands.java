package com.mewna.discord.shard;

import com.google.common.collect.ImmutableList;
import com.mewna.catnip.entity.channel.Channel;
import com.mewna.catnip.entity.channel.GuildChannel;
import com.mewna.catnip.entity.guild.Guild;
import com.mewna.catnip.entity.guild.Role;
import com.mewna.catnip.entity.user.User;
import gg.amy.catnip.utilities.menu.MenuExtension;
import gg.amy.catnip.utilities.typesafeCommands.Command;
import gg.amy.catnip.utilities.typesafeCommands.Context;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collection;

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
            final Collection<String> pages = new ArrayList<>();
            if(user != null) {
                pages.add(String.format("%s#%s (%s)", user.username(), user.discriminator(), user.id()));
            }
            if(guild != null) {
                pages.add(String.format("%s (%s) in %s", guild.name(), guild.id(), guild.region()));
            }
            if(channel != null && channel.isGuild()) {
                final GuildChannel gc = channel.asGuildChannel();
                pages.add(String.format("#%s (%s - %s)", gc.name(), gc.guildId(), gc.id()));
            }
            if(roles != null) {
                roles.roles().forEach(r -> {
                    pages.add(String.format("%s 0x%s (%s - %s)", r.name(), Integer.toHexString(r.color()), r.guildId(), r.id()));
                });
            }
            if(channels != null) {
                channels.channels().forEach(c -> {
                    final GuildChannel gc = c.asGuildChannel();
                    pages.add(String.format("#%s (%s - %s)", gc.name(), gc.guildId(), gc.id()));
                });
            }
            
            //noinspection ConstantConditions
            ctx.catnip().extensionManager().extension(MenuExtension.class)
                    .createPaginatedMenu("Search results:", ImmutableList.copyOf(pages))
                    .accept(ctx.source().author(), ctx.source().channelId());
        }
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
