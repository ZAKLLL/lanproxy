package org.fengfei.lanproxy.client.udp;

import lombok.SneakyThrows;
import org.fengfei.lanproxy.protocol.ProxyMessage;
import org.fengfei.lanproxy.protocol.UdpProxyMessageCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class UdpToUdpClient {

    private static final AtomicLong userIdProducer = new AtomicLong(0);


    static byte[] udpPolePunchingInfo = new byte[128];

    static {
        for (int i = 0; i < 128; i++) {
            if ((i & 1) == 0) {
                udpPolePunchingInfo[i] = Byte.MAX_VALUE;
            } else {
                udpPolePunchingInfo[i] = Byte.MIN_VALUE;
            }
        }
    }

    private static boolean checkUdpPole(byte[] sendInfo, byte[] receiveInfo) {
        for (int i = 0; i < sendInfo.length; i++) {
            if (sendInfo[i] != receiveInfo[i]) return false;
        }
        return true;
    }


    public static void main(String[] args) throws IOException {
        final String centerServerIp = "127.0.0.1";
        final Integer centerServerPort = 7777;


        int locolUdpSocketPort = 7771;
        String clientKey = "82e3e7294cfc41b49cbc1a906646e050";
        String targetAddressInfo = "127.0.0.1:8888";

        Scanner scanner = new Scanner(System.in);

        System.out.print("请输入本地Udp发包端口(默认为7771):");
        String portStr = scanner.nextLine();
        if (portStr != null && portStr.trim().length() != 0) {
            locolUdpSocketPort = Integer.parseInt(portStr);
        }


        System.out.print("请输入ClientKey(eg: 82e3e7294cfc41b49cbc1a906646e050):");
        clientKey = scanner.next();
        System.out.print("请输入目标局域网服务地址(eg: 127.0.0.1:8888):");
        targetAddressInfo = scanner.next();

        System.out.println("start connect to center server");
        final DatagramSocket udpSocket = new DatagramSocket(locolUdpSocketPort);

        udpSocket.setSoTimeout(1000 * 1000);


        byte[] bytes = (clientKey + "-" + targetAddressInfo).getBytes();

        //connect to center server
        udpSocket.send(new DatagramPacket(bytes, bytes.length, new InetSocketAddress(centerServerIp, centerServerPort)));
        byte[] receivedPunchingInfo = new byte[128];

        //第一次从client端接收到的udp打洞信息
        final DatagramPacket receiveP = new DatagramPacket(receivedPunchingInfo, receivedPunchingInfo.length);
        udpSocket.receive(receiveP);
        udpSocket.send(new DatagramPacket(udpPolePunchingInfo, udpPolePunchingInfo.length, receiveP.getSocketAddress()));

        final InetAddress clientAddress = receiveP.getAddress();

        boolean connected = checkUdpPole(receivedPunchingInfo, udpPolePunchingInfo);
        if (connected) {
            System.out.println("connect success:" + clientAddress.getHostAddress() + ":" + receiveP.getPort());
        }


        //todo 开一个线程用来维持心跳


        final byte[] byteBuf = new byte[2048 * 10];

        final DatagramPacket dataReceiveP = new DatagramPacket(byteBuf, byteBuf.length);


        new Thread(new Runnable() {
            @SneakyThrows
            @Override
            public void run() {
                int localUdp2Port = -1;
                String localUdp2Ip = "";

                while (true) {
                    udpSocket.receive(dataReceiveP);
                    byte[] proxyMsgBytes = new byte[dataReceiveP.getLength()];
                    System.arraycopy(byteBuf, dataReceiveP.getOffset(), proxyMsgBytes, 0, dataReceiveP.getLength());

                    System.out.println("---------------------->" + dataReceiveP.getAddress() + ":" + dataReceiveP.getPort());
                    System.out.println(dataReceiveP.getAddress() + Arrays.toString(proxyMsgBytes));
                    //代理机器->本地服务
                    if (dataReceiveP.getPort() == 8769) {
                        udpSocket.send(new DatagramPacket(proxyMsgBytes, proxyMsgBytes.length, new InetSocketAddress(localUdp2Ip, localUdp2Port)));
                    } else {
                        //本地服务->代理服务
                        localUdp2Port = dataReceiveP.getPort();
                        localUdp2Ip = dataReceiveP.getAddress().getHostAddress();
                        udpSocket.send(new DatagramPacket(proxyMsgBytes, proxyMsgBytes.length, new InetSocketAddress(clientAddress, 8769)));
                    }

                }
            }
        }).start();


    }

    private static String newUserId() {
        return String.valueOf(userIdProducer.incrementAndGet());
    }
}
