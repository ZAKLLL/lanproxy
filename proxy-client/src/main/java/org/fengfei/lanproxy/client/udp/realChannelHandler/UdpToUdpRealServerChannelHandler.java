package org.fengfei.lanproxy.client.udp.realChannelHandler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.fengfei.lanproxy.protocol.Constants;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * @author ZhangJiaKui
 * @classname UdpRealServerChannelHandler
 * @description TODO
 * @date 5/21/2021 11:39 AM
 */
public class UdpToUdpRealServerChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket datagramPacket) throws Exception {
        String userClientAddress = ctx.channel().attr(Constants.UDP_USER_CLIENT_IP).get();
        String[] userClientIpInfos = userClientAddress.split(":");
        byte[] data = datagramPacket.getData();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(data, data.length, new InetSocketAddress(userClientIpInfos[0], Integer.parseInt(userClientIpInfos[1]))));
        }
    }


}
