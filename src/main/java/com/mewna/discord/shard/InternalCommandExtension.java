package com.mewna.discord.shard;

import com.mewna.catnip.entity.message.Message;
import com.mewna.catnip.entity.message.MessageType;
import com.mewna.catnip.extension.AbstractExtension;
import com.mewna.catnip.shard.DiscordEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author amy
 * @since 12/14/18.
 */
@SuppressWarnings("unused")
public class InternalCommandExtension extends AbstractExtension {
    private static final String INTERNAL_PREFIX = "amyware!";
    
    private final MewnaShard shard;
    
    private final Map<String, Consumer<Message>> commands = new ConcurrentHashMap<>();
    
    @SuppressWarnings("WeakerAccess")
    public InternalCommandExtension(final MewnaShard shard) {
        super("internal-commands");
        this.shard = shard;
    }
    
    @SuppressWarnings("ConstantConditions")
    @Override
    public void start() {
        //noinspection CodeBlock2Expr
        commands.put("stats", msg -> {
            msg.channel().sendMessage("Stats:\n" +
                    "```CSS\n" +
                    "  [guilds] " + catnip().cache().guilds().size() + '\n' +
                    "   [users] " + catnip().cache().users().size() + '\n' +
                    " [members] " + catnip().cache().members().size() + '\n' +
                    "[channels] " + catnip().cache().channels().size() + '\n' +
                    "   [roles] " + catnip().cache().roles().size() + '\n' +
                    "```");
        });
        //noinspection CodeBlock2Expr
        commands.put("here", msg -> {
            msg.channel().sendMessage("Stats:\n" +
                    "```CSS\n" +
                    "   [guild] " + msg.guildId() + '\n' +
                    "   [users] " + catnip().cache().users().size() + '\n' +
                    " [members] " + msg.guild().memberCount() + '\n' +
                    "[channels] " + msg.guild().channels().size() + '\n' +
                    "   [roles] " + msg.guild().roles().size() + '\n' +
                    "```");
        });
        commands.put("ram", msg -> {
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
            
            msg.channel().sendMessage("RAM:\n" +
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
        });

        catnip().on(DiscordEvent.MESSAGE_CREATE, msg -> {
            if(msg.type() == MessageType.DEFAULT) {
                if(msg.guildId() != null && msg.member() != null) {
                    if(msg.author().id().equalsIgnoreCase("128316294742147072")
                            && msg.content().startsWith(INTERNAL_PREFIX)) {
                        final var c = msg.content().substring(INTERNAL_PREFIX.length());
                        final var split = c.split("\\s+", 2);
                        final var name = split[0].toLowerCase();
                        final var args = split.length > 1 ? split[1] : "";
                        
                        if(commands.containsKey(name)) {
                            commands.get(name).accept(msg);
                        } else {
                            msg.channel().sendMessage("what is " + c);
                        }
                    }
                }
            }
        });
    }
}
