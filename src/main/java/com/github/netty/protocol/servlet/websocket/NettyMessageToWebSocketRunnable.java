package com.github.netty.protocol.servlet.websocket;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import com.github.netty.core.util.TypeUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.*;

import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * WebSocketMessageToRunnable
 * @author wangzihao
 */
public class NettyMessageToWebSocketRunnable implements MessageToRunnable {
    private static final Recycler<WebsocketRunnable> RECYCLER = new Recycler<>(WebsocketRunnable::new);

    private MessageToRunnable parent;

    public NettyMessageToWebSocketRunnable(MessageToRunnable parent) {
        this.parent = parent;
    }

    @Override
    public Runnable newRunnable(ChannelHandlerContext channelHandlerContext, Object msg) {
        if(msg instanceof WebSocketFrame) {
            WebsocketRunnable task = RECYCLER.getInstance();
            task.context = channelHandlerContext;
            task.frame = (WebSocketFrame) msg;
            return task;
        }
        if(parent != null){
            return parent.newRunnable(channelHandlerContext,msg);
        }
        throw new IllegalStateException("["+msg.getClass().getName()+"] Message data type that cannot be processed");
    }

    /**
     * Websocket task
     */
    public static class WebsocketRunnable implements Runnable, Recyclable {
        private ChannelHandlerContext context;
        private WebSocketFrame frame;

        public WebSocketSession getWebSocketSession(){
            return WebSocketSession.getSession(context.channel());
        }

        public WebSocketFrame getFrame() {
            return frame;
        }

        public void setFrame(WebSocketFrame frame) {
            this.frame = frame;
        }

        public ChannelHandlerContext getContext() {
            return context;
        }

        @Override
        public void run() {
            try {
                WebSocketSession wsSession = getWebSocketSession();
                if (wsSession == null) {
                    return;
                }

                // Close the message
                if (frame instanceof CloseWebSocketFrame) {
                    wsSession.getWebSocketServerHandshaker().close(context.channel(), (CloseWebSocketFrame) frame.retain());
                    return;
                }

                // Ping message
                if (frame instanceof PingWebSocketFrame) {
                    ByteBuffer request = frame.content().nioBuffer();
                    onWebsocketMessage(wsSession, frame, (PongMessage) () -> request);
                    return;
                }

                // Binary message
                if (frame instanceof BinaryWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, frame.content().nioBuffer());
                    return;
                }

                // String message
                if (frame instanceof TextWebSocketFrame) {
                    onWebsocketMessage(wsSession, frame, ((TextWebSocketFrame) frame).text());
                }
            }finally {
                WebsocketRunnable.this.recycle();
            }
        }

        private void onWebsocketMessage(WebSocketSession wsSession, WebSocketFrame frame, Object message){
            Class messageType = message.getClass();

            Set<MessageHandler> messageHandlers = wsSession.getMessageHandlers();
            for(MessageHandler handler : messageHandlers){
                if(handler instanceof MessageHandler.Partial){
                    MessageHandler.Partial<Object> partial =((MessageHandler.Partial<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Partial.class, partial.getClass());
                    if(typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz().isAssignableFrom(messageType)){
                        partial.onMessage(message,frame.isFinalFragment());
                    }
                    continue;
                }

                if(handler instanceof MessageHandler.Whole){
                    MessageHandler.Whole<Object> whole =((MessageHandler.Whole<Object>) handler);
                    TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(MessageHandler.Whole.class,whole.getClass());
                    if(typeResult == null
                            || typeResult.getClazz() == Object.class
                            || typeResult.getClazz().isAssignableFrom(messageType)){
                        whole.onMessage(message);
                    }
                }
            }
        }

        @Override
        public void recycle() {
            if(context instanceof Recyclable) {
                ((Recyclable) context).recycle();
            }
            context = null;
            frame = null;
        }
    }

}
