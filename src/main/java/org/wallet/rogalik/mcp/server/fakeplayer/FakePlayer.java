package org.wallet.rogalik.mcp.server.fakeplayer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * A server-side player with no client behind it.
 *
 * <p>Deliberately an ordinary {@link ServerPlayer} registered in the player list: {@code @a} and
 * {@code @p} must select it, scoreboards must score it, and death, respawn and disconnect must
 * take their normal paths. Nothing anywhere checks "is this a fake" and skips — a test that runs
 * against special-cased code proves nothing about the real thing.
 *
 * <p>The only pretence is the network: packets are written into a discarded in-memory channel,
 * so the vanilla code that sends them runs unchanged and the bytes go nowhere.
 */
public final class FakePlayer extends ServerPlayer {

    private FakePlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile, ClientInformation.createDefault());
    }

    /**
     * Creates the player and puts it in the player list.
     *
     * @return the placed player, ready for commands to address by name
     */
    public static FakePlayer spawn(
        MinecraftServer server,
        ServerLevel level,
        String name,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        GameType gameMode
    ) {
        // Offline-mode UUID derived from the name, so scoreboard entries and statistics survive
        // a despawn and respawn. A random UUID would lose them, and the role model in most
        // datapacks is built on scoreboard values.
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);

        FakePlayer player = new FakePlayer(server, level, profile);
        player.snapTo(x, y, z, yaw, pitch);

        // Fully qualified: ServerPlayer brings WaypointTransmitter.Connection into scope, which
        // shadows the network one.
        net.minecraft.network.Connection connection = discardingConnection();
        server.getPlayerList().placeNewPlayer(
            connection, player, CommonListenerCookie.createInitial(profile, false));

        player.setGameMode(gameMode);
        return player;
    }

    /**
     * A connection over an in-memory channel that throws every outbound packet away.
     *
     * <p>Using a real channel rather than a stubbed-out {@link Connection} keeps the vanilla send
     * path intact, so nothing downstream has to know this player has no client.
     */
    private static net.minecraft.network.Connection discardingConnection() {
        net.minecraft.network.Connection connection =
            new net.minecraft.network.Connection(PacketFlow.SERVERBOUND);

        ChannelOutboundHandlerAdapter discard = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                // Release immediately; otherwise every packet ever sent to this player is retained.
                ReferenceCountUtil.release(message);
                promise.setSuccess();
            }
        };

        // Outbound messages travel from the tail towards the head, so the discarding handler must
        // sit closer to the head than the connection for it to swallow what the connection writes.
        // Passing both to the constructor also registers the channel and fires channelActive,
        // which is what gives the connection its channel reference.
        new EmbeddedChannel(discard, connection);
        return connection;
    }
}
