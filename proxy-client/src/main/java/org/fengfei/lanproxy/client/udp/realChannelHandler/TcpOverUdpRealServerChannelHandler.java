package org.fengfei.lanproxy.client.udp.realChannelHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.fengfei.lanproxy.protocol.Constants;
import org.fengfei.lanproxy.protocol.ProxyMessage;
import org.fengfei.lanproxy.protocol.UdpProxyMessageCodec;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * @author ZhangJiaKui
 * @classname UdpRealServerChannelHandler
 * @description TODO
 * @date 5/21/2021 11:39 AM
 */
public class TcpOverUdpRealServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            String userClientAddress = ctx.channel().attr(Constants.UDP_USER_CLIENT_IP).get();
            String userId = ctx.channel().attr(Constants.USER_ID).get();
            String[] userClientIpInfos = userClientAddress.split(":");
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);

            ProxyMessage responseMsg = new ProxyMessage(ProxyMessage.P_TYPE_TRANSFER_TCP_OVER_UDP, bytes.length, userId, bytes);

            byte[] dataBytes = UdpProxyMessageCodec.encode(responseMsg);

            socket.send(new DatagramPacket(dataBytes, dataBytes.length, new InetSocketAddress(userClientIpInfos[0], Integer.parseInt(userClientIpInfos[1]))));
        }

    }

}
