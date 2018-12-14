package com.mewna.discord.shard;

import com.mewna.catnip.entity.message.Message;
import com.mewna.catnip.entity.message.MessageType;
import com.mewna.catnip.extension.AbstractExtension;
import com.mewna.catnip.shard.DiscordEvent;
import io.sentry.Sentry;
import io.vertx.core.json.JsonObject;

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
            shard.pubsub("stats", new JsonObject())
                    .thenAccept(objs -> {
                        int guilds = 0;
                        int users = 0;
                        int members = 0;
                        int channels = 0;
                        int roles = 0;
                        for(final JsonObject obj : objs) {
                            guilds += obj.getInteger("guilds");
                            users += obj.getInteger("users");
                            members += obj.getInteger("members");
                            channels += obj.getInteger("channels");
                            roles += obj.getInteger("roles");
                        }
                        msg.channel().sendMessage("Stats:\n" +
                                "```CSS\n" +
                                "  [guilds] " + guilds + '\n' +
                                "   [users] " + users + '\n' +
                                " [members] " + members + '\n' +
                                "[channels] " + channels + '\n' +
                                "   [roles] " + roles + '\n' +
                                "```");
                    });
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
        //noinspection CodeBlock2Expr
        commands.put("ram", msg -> {
            shard.pubsub("ram", new JsonObject()).thenAccept(objs -> {
                try {
                    long heapUsed = 0;
                    long heapAllocated = 0;
                    long heapTotal = 0;
                    long heapInit = 0;
                    long nonHeapUsed = 0;
                    long nonHeapAllocated = 0;
                    long nonHeapTotal = 0;
                    long nonHeapInit = 0;
                    
                    for(final JsonObject o : objs) {
                        try {
                            final JsonObject heap = o.getJsonObject("heap");
                            final JsonObject nonHeap = o.getJsonObject("nonheap");
                            
                            heapUsed += heap.getLong("used");
                            heapAllocated += heap.getLong("allocated");
                            heapTotal += heap.getLong("total");
                            heapInit += heap.getLong("init");
                            
                            nonHeapUsed += nonHeap.getLong("used");
                            nonHeapAllocated += nonHeap.getLong("allocated");
                            nonHeapTotal += nonHeap.getLong("total");
                            nonHeapInit += nonHeap.getLong("init");
                        } catch(final Exception e) {
                            Sentry.capture(e);
                        }
                    }
                    
                    heapUsed /= 1024L * 1024L;
                    heapAllocated /= 1024L * 1024L;
                    heapTotal /= 1024L * 1024L;
                    heapInit /= 1024L * 1024L;
                    nonHeapUsed /= 1024L * 1024L;
                    nonHeapAllocated /= 1024L * 1024L;
                    nonHeapTotal /= 1024L * 1024L;
                    nonHeapInit /= 1024L * 1024L;
                    
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
                } catch(final Exception e) {
                    Sentry.capture(e);
                }
            }).exceptionally(e -> {
                Sentry.capture(e);
                return null;
            });
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
