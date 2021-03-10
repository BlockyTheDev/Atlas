package cc.funkemunky.api.packet.channel;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.reflections.Reflections;
import cc.funkemunky.api.reflections.impl.MinecraftReflection;
import cc.funkemunky.api.reflections.types.WrappedClass;
import cc.funkemunky.api.reflections.types.WrappedField;
import cc.funkemunky.api.utils.MiscUtils;
import net.minecraft.util.com.mojang.authlib.GameProfile;
import net.minecraft.util.io.netty.channel.*;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class ChannelOld implements ChannelListener {

    private final Map<String, Channel> channelCache = new WeakHashMap<>();
    private final Map<Channel, Integer> versionCache = new HashMap<>();
    private static final WrappedClass classPacketSetProtocol
            = Reflections.getNMSClass("PacketHandshakingInSetProtocol"),
            classLoginStart = Reflections.getNMSClass("PacketLoginInStart");

    //TODO Check if this is the case for all versions cause Mojang mightve done something dumb in between.
    private static final WrappedField fieldFutureList = MinecraftReflection.serverConnection
            .getFieldByType(List.class, 0);
    private static final WrappedField fieldProtocolId = classPacketSetProtocol.getFieldByType(int.class, 0),
            fieldProtocolType = classPacketSetProtocol.getFieldByType(Enum.class, 0),
            fieldGameProfile = classLoginStart.getFieldByType(GameProfile.class, 0);

    private ChannelInboundHandlerAdapter serverRegisterHandler;
    private ChannelInitializer<Channel> hackyRegister, channelRegister;
    private boolean injectedServerChannel;
    private static String handle = "atlas_packet_listener";

    /* private static final FieldAccessor<Integer> protocolId = Reflection.getField(PACKET_SET_PROTOCOL, int.class, 0);
	private static final FieldAccessor<Enum> protocolType = Reflection.getField(PACKET_SET_PROTOCOL, Enum.class, 0); */

    public ChannelOld() {
        System.out.println("Running executor for server registering...");
        System.out.println("Running registration...");
        List<ChannelFuture> futures = fieldFutureList.get(MinecraftReflection.getServerConnection());
        channelRegister = new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel channel) {
                try {
                    inject(channel);
                } catch(Exception e) {
                    System.out.println("Error injecting into channel " + channel.toString());
                }
            }

        };

        hackyRegister = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                channel.pipeline().addLast(channelRegister);
            }
        };

        serverRegisterHandler = new ChannelInboundHandlerAdapter() {

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                Channel channel = (Channel) msg;
                channel.pipeline().addFirst(hackyRegister);
                ctx.fireChannelRead(msg);
            }
        };

        new BukkitRunnable() {
            @Override
            public void run() {
                futures.forEach(future -> {
                    Channel channel = future.channel();

                    channel.pipeline().addFirst(serverRegisterHandler);

                    MiscUtils.printToConsole("Injected server channel " + channel.toString());

                    injectedServerChannel = true;
                });
            }
        }.runTask(Atlas.getInstance());
    }

    @Override
    public int getProtocolVersion(Player player) {
        Channel channel = getChannel(player);

        return versionCache.getOrDefault(channel, -1);
    }

    @Override
    public void inject(Player player) {
        if(!Atlas.getInstance().isEnabled()) return;
        Channel channel = getChannel(player);

        if(channel == null) return;

        channel.eventLoop().execute(() -> {
            Listen listen = (Listen) channel.pipeline().get(handle);

            if(listen == null) {
                listen = new Listen(player);

                if(channel.pipeline().get(handle) != null) {
                    channel.pipeline().remove(handle);
                }
                channel.pipeline().addBefore("packet_handler", handle, listen);
            }
        });
    }

    public void inject(Channel channel) {
        if(!Atlas.getInstance().isEnabled()) return;

        channel.eventLoop().execute(() -> {
            Listen listen = (Listen) channel.pipeline().get(handle);

            if(listen == null) {
                listen = new Listen(null);

                if(channel.pipeline().get(handle) != null) {
                    channel.pipeline().remove(handle);
                }
                channel.pipeline().addBefore("packet_handler", handle, listen);
            }
        });
    }

    @Override
    public void uninject(Player player) {
        Channel channel = getChannel(player);

        unject(channel);

        channelCache.remove(player.getName());
        versionCache.remove(channel);
    }

    public void unject(Channel channel) {
        channel.eventLoop().execute(() -> {
            if(channel.pipeline().get(handle) != null) {
                channel.pipeline().remove(handle);
            }
        });
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        getChannel(player).pipeline().writeAndFlush(packet);
    }

    @Override
    public void receivePacket(Player player, Object packet) {
        getChannel(player).pipeline().context("encoder").fireChannelRead(packet);
    }

    private Channel getChannel(Player player) {
        return channelCache.compute(player.getName(), (key, channel) -> {
           if(channel == null) {
               return MinecraftReflection.getChannel(player);
           }
           return channel;
        });
    }

    public Object onReceive(Player player, Object packet) {
        return packet;
    }

    public Object onSend(Player player, Object packet) {
        return packet;
    }

    public Object onHandshake(SocketAddress address, Object packet) {
        return packet;
    }

    @RequiredArgsConstructor
    public class Listen extends ChannelDuplexHandler {
        final Player player;
        @Override
        public void channelRead(ChannelHandlerContext context, Object o) throws Exception {
            Object object = o;

            if(player != null) {
                object = onReceive(player, object);

            } else object = onHandshake(context.channel().remoteAddress(), o);

            if(classLoginStart.getParent().isInstance(o)) {
                GameProfile profile = fieldGameProfile.get(o);

                channelCache.put(profile.getName(), context.channel());
            } else if (classPacketSetProtocol.getParent().isInstance(o)) {
                String protocol = ((Enum)fieldProtocolType.get(o)).name();
                if (protocol.equalsIgnoreCase("LOGIN")) {
                    int id = fieldProtocolId.get(o);
                    versionCache.put(context.channel(), id);
                }
            }

            if(object != null) {
                super.channelRead(context, object);
            }
        }

        @Override
        public void write(ChannelHandlerContext context, Object o, ChannelPromise promise) throws Exception {
            Object object = o;

            if(player != null) {
                object = onSend(player, object);
            }  else object = onHandshake(context.channel().remoteAddress(), o);

            if(object != null) {
                super.write(context, object, promise);
            }
        }
    }
}
