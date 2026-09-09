package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.util.VisibleForDebug;
import org.slf4j.Logger;

public abstract class ServerCommonPacketListenerImpl implements ServerCommonPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LATENCY_CHECK_INTERVAL = 15000;
    private static final int CLOSED_LISTENER_TIMEOUT = 15000;
    private static final Component TIMEOUT_DISCONNECTION_MESSAGE = Component.translatable("disconnect.timeout");
    static final Component DISCONNECT_UNEXPECTED_QUERY = Component.translatable("multiplayer.disconnect.unexpected_query_response");
    protected final MinecraftServer server;
    protected final Connection connection;
    private final boolean transferred;
    private long keepAliveTime;
    private boolean keepAlivePending;
    private long keepAliveChallenge;
    private long closedListenerTime;
    private boolean closed = false;
    private int latency;
    private volatile boolean suspendFlushingOnServerThread = false;
    /**
     * Holds the current connection type, based on the types of payloads that have been received so far.
     */
    protected net.neoforged.neoforge.network.connection.ConnectionType connectionType;

    public ServerCommonPacketListenerImpl(MinecraftServer server, Connection connection, CommonListenerCookie cookie) {
        this.server = server;
        this.connection = connection;
        this.keepAliveTime = Util.getMillis();
        this.latency = cookie.latency();
        this.transferred = cookie.transferred();
        // Neo: Set the connection type based on the cookie from the previous phase.
        this.connectionType = cookie.connectionType();
    }

    private void close() {
        if (!this.closed) {
            this.closedListenerTime = Util.getMillis();
            this.closed = true;
        }
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        if (this.isSingleplayerOwner()) {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.halt(false);
        }
    }

    @Override
    public void handleKeepAlive(ServerboundKeepAlivePacket packet) {
        if (this.keepAlivePending && packet.getId() == this.keepAliveChallenge) {
            int i = (int)(Util.getMillis() - this.keepAliveTime);
            this.latency = (this.latency * 3 + i) / 4;
            this.keepAlivePending = false;
        } else if (!this.isSingleplayerOwner()) {
            this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
        }
    }

    @Override
    public void handlePong(ServerboundPongPacket packet) {
    }

    @Override
    public void handleCustomPayload(ServerboundCustomPayloadPacket packet) {
        // Neo: Unconditionally handle register/unregister payloads.
        if (packet.payload() instanceof net.neoforged.neoforge.network.payload.MinecraftRegisterPayload minecraftRegisterPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.onMinecraftRegister(this.getConnection(), minecraftRegisterPayload.newChannels());
            return;
        }

        if (packet.payload() instanceof net.neoforged.neoforge.network.payload.MinecraftUnregisterPayload minecraftUnregisterPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.onMinecraftUnregister(this.getConnection(), minecraftUnregisterPayload.forgottenChannels());
            return;
        }

        if (packet.payload() instanceof net.neoforged.neoforge.network.payload.CommonVersionPayload commonVersionPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.checkCommonVersion(this, commonVersionPayload);
            return;
        }

        if (packet.payload() instanceof net.neoforged.neoforge.network.payload.CommonRegisterPayload commonRegisterPayload) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.onCommonRegister(this, commonRegisterPayload);
            return;
        }

        // Neo: Handle modded payloads. Vanilla payloads do not get sent to the modded handling pass. Additional payloads cannot be registered in the minecraft domain.
        if (net.neoforged.neoforge.network.registration.NetworkRegistry.isModdedPayload(packet.payload())) {
            net.neoforged.neoforge.network.registration.NetworkRegistry.handleModdedPayload(this, packet);
            return;
        }
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.server);
        if (packet.action() == ServerboundResourcePackPacket.Action.DECLINED && this.server.isResourcePackRequired()) {
            LOGGER.info("Disconnecting {} due to resource pack {} rejection", this.playerProfile().getName(), packet.id());
            this.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
        }
    }

    @Override
    public void handleCookieResponse(ServerboundCookieResponsePacket packet) {
        this.disconnect(DISCONNECT_UNEXPECTED_QUERY);
    }

    protected void keepConnectionAlive() {
        this.server.getProfiler().push("keepAlive");
        long i = Util.getMillis();
        if (!this.isSingleplayerOwner() && i - this.keepAliveTime >= 15000L) {
            if (this.keepAlivePending) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            } else if (this.checkIfClosed(i)) {
                this.keepAlivePending = true;
                this.keepAliveTime = i;
                this.keepAliveChallenge = i;
                this.send(new ClientboundKeepAlivePacket(this.keepAliveChallenge));
            }
        }

        this.server.getProfiler().pop();
    }

    private boolean checkIfClosed(long time) {
        if (this.closed) {
            if (time - this.closedListenerTime >= 15000L) {
                this.disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            }

            return false;
        } else {
            return true;
        }
    }

    public void suspendFlushing() {
        this.suspendFlushingOnServerThread = true;
    }

    public void resumeFlushing() {
        this.suspendFlushingOnServerThread = false;
        this.connection.flushChannel();
    }

    public void send(Packet<?> packet) {
        this.send(packet, null);
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        net.neoforged.neoforge.network.registration.NetworkRegistry.checkPacket(packet, this);

        if (packet.isTerminal()) {
            this.close();
        }

        boolean flag = !this.suspendFlushingOnServerThread || !this.server.isSameThread();

        try {
            this.connection.send(packet, listener, flag);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Sending packet");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Packet being sent");
            crashreportcategory.setDetail("Packet class", () -> packet.getClass().getCanonicalName());
            throw new ReportedException(crashreport);
        }
    }

    public void disconnect(Component reason) {
        this.disconnect(new DisconnectionDetails(reason));
    }

    public void disconnect(DisconnectionDetails disconnectionDetails) {
        this.connection.send(new ClientboundDisconnectPacket(disconnectionDetails.reason()), PacketSendListener.thenRun(() -> this.connection.disconnect(disconnectionDetails)));
        this.connection.setReadOnly();
        this.server.executeBlocking(this.connection::handleDisconnection);
    }

    protected boolean isSingleplayerOwner() {
        return this.server.isSingleplayerOwner(this.playerProfile());
    }

    protected abstract GameProfile playerProfile();

    @VisibleForDebug
    public GameProfile getOwner() {
        return this.playerProfile();
    }

    public int latency() {
        return this.latency;
    }

    /**
     * Creates a new cookie for this connection.
     *
     * @param clientInformation The client information.
     * @deprecated Use {@link #createCookie(ClientInformation,
     *             net.neoforged.neoforge.network.connection.ConnectionType)} instead,
     *             keeping the connection type information available.
     * @return The cookie.
     */
    @Deprecated
    protected CommonListenerCookie createCookie(ClientInformation clientInformation) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, clientInformation, this.transferred);
    }

    /**
     * Creates a new cookie for this connection.
     *
     * @param clientInformation The client information.
     * @param connectionType    Whether the connection is modded.
     * @return The cookie.
     */
    protected CommonListenerCookie createCookie(ClientInformation clientInformation, net.neoforged.neoforge.network.connection.ConnectionType connectionType) {
        return new CommonListenerCookie(this.playerProfile(), this.latency, clientInformation, this.transferred, connectionType);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public net.minecraft.util.thread.ReentrantBlockableEventLoop<?> getMainThreadEventLoop() {
        return server;
    }

    @Override
    public net.neoforged.neoforge.network.connection.ConnectionType getConnectionType() {
        return connectionType;
    }
}
