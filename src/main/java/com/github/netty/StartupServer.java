package com.github.netty;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.Ordered;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.HostUtil;
import com.github.netty.core.util.ServerInfo;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.TcpChannel;
import com.github.netty.protocol.servlet.ServletContext;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.PlatformDependent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Startup Server
 * you config this.
 * @author wangzihaogithub
 * 2020-06-06 17:48:28
 */
public class StartupServer extends AbstractNettyServer {
    private final Collection<ProtocolHandler> protocolHandlers = new TreeSet<>(Ordered.COMPARATOR);
    private final Collection<ServerListener> serverListeners = new TreeSet<>(Ordered.COMPARATOR);
    private DynamicProtocolChannelHandler dynamicProtocolChannelHandler = new DynamicProtocolChannelHandler();

    public StartupServer(int port){
        this(getServerSocketAddress(null,port));
    }

    public StartupServer(InetSocketAddress serverAddress){
        super(serverAddress);
    }

    public void start() throws IllegalStateException {
        try{
            super.init();
            for(ServerListener serverListener : serverListeners){
                serverListener.onServerStart(this);
            }
            super.run();
        } catch (Exception e) {
            throw new IllegalStateException("tcp server start fail.. cause = " + e,e);
        }
    }

    @Override
    public void stop() throws IllegalStateException {
        for(ServerListener serverListener : serverListeners){
            try {
                serverListener.onServerStop(this);
            }catch (Throwable t){
                logger.error("case by stop event [" + t.getMessage()+"]",t);
            }
        }

        try{
            super.stop();
            for (TcpChannel tcpChannel : TcpChannel.getChannels().values()) {
                tcpChannel.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(),e);
        }
    }

    @Override
    protected void startAfter(ChannelFuture future){
        //Exception thrown
        Throwable cause = future.cause();
        if(cause != null){
            PlatformDependent.throwException(cause);
        }

        logger.info("{} start (version = {}, port = {}, pid = {}, protocol = {}, os = {}) ...",
                getName(),
                ServerInfo.getServerNumber(),
                getPort()+"",
                HostUtil.getPid()+"",
                protocolHandlers,
                HostUtil.getOsName()
        );
    }

    /**
     * 自适应写（maxBytesPerGatheringWrite）：
     * Netty 批量写数据时，如果想写的都写进去了，接下来的尝试写更多（调整 maxBytesPerGatheringWrite）。
     *
     * 连续写（writeSpinCount）：
     * 同连接读一样，每个连接默认最多连续写 16 次，即使还有数据也暂时不处理了，先处理下一个连接。
     *
     * 注册 OP_WRITE 事件
     * 如果 socket sendbuf 已经写不动，那就注册 OP_WRITE 事件。当触发 OP_WRITE 事件时，则取消 OP_WRITE 事件，并继续写。
     *
     * 高低水位线（writeBufferWaterMark）
     * Netty 待写数据太多，超过一定的水位线（writeBufferWaterMark.high()），会将可写的标志位改成
     * false ，让应用自己做决定要不要发送数据了。
     *
     * @param bootstrap bootstrap
     * @throws Exception Exception
     */
    @Override
    protected void config(ServerBootstrap bootstrap) throws Exception{
        super.config(bootstrap);
        if(SystemPropertyUtil.get("io.netty.leakDetectionLevel") == null &&
                SystemPropertyUtil.get("io.netty.leakDetection.level") == null){
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        }
        if(SystemPropertyUtil.get("io.netty.maxDirectMemory") == null){
            long maxDirectMemory = -1;
            System.setProperty("io.netty.maxDirectMemory", String.valueOf(maxDirectMemory));
        }
        bootstrap.childOption(ChannelOption.WRITE_SPIN_COUNT,Integer.MAX_VALUE);
        bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024,Integer.MAX_VALUE));
        bootstrap.childOption(ChannelOption.AUTO_CLOSE,true);

        bootstrap.childOption(ChannelOption.TCP_NODELAY, false);
        for (ServerListener serverListener : serverListeners) {
            serverListener.config(bootstrap);
        }
    }

    /**
     * Initializes the IO executor
     * @return DynamicProtocolChannelHandler
     */
    @Override
    protected ChannelHandler newWorkerChannelHandler() {
        //Dynamic protocol processor
        DynamicProtocolChannelHandler handler = dynamicProtocolChannelHandler;
        handler.setProtocolHandlers(protocolHandlers);
        return handler;
    }

    public ServletContext getServletContext(){
        for(ProtocolHandler protocolHandler : protocolHandlers){
            if(protocolHandler instanceof HttpServletProtocol){
                return ((HttpServletProtocol) protocolHandler).getServletContext();
            }
        }
        return null;
    }

    /**
     * Gets the protocol registry list
     * @return protocolHandlers
     */
    public Collection<ProtocolHandler> getProtocolHandlers(){
        return protocolHandlers;
    }

    /**
     * Gets the server listener list
     * @return serverListeners
     */
    public Collection<ServerListener> getServerListeners() {
        return serverListeners;
    }

    public DynamicProtocolChannelHandler getDynamicProtocolChannelHandler() {
        return dynamicProtocolChannelHandler;
    }

    public void setDynamicProtocolChannelHandler(DynamicProtocolChannelHandler dynamicProtocolChannelHandler) {
        this.dynamicProtocolChannelHandler = dynamicProtocolChannelHandler;
    }

    public static InetSocketAddress getServerSocketAddress(InetAddress address, int port) {
        if(address == null) {
            try {
                address = InetAddress.getByAddress(new byte[]{0,0,0,0});
                if(!address.isAnyLocalAddress()){
                    address = InetAddress.getByName("::1");
                }
                if(!address.isAnyLocalAddress()){
                    address = new InetSocketAddress(port).getAddress();
                }
            } catch (UnknownHostException e) {
                address = new InetSocketAddress(port).getAddress();
            }
        }
        return new InetSocketAddress(address,port);
    }

}
