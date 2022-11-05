package org.fengfei.lanparoxy.client.test;

import lombok.SneakyThrows;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class UdpServerTest {

    @SneakyThrows
    public static void main(String[] args) {
        DatagramSocket uSocket = new DatagramSocket(8989);
        uSocket.setSoTimeout(1000*100000);
        final byte[] byteBuf = new byte[2048 * 10];

        final DatagramPacket dataReceiveP = new DatagramPacket(byteBuf, byteBuf.length);

        while (true){
            uSocket.receive(dataReceiveP);
            byte[] data = dataReceiveP.getData();
            int length = dataReceiveP.getLength();
            System.out.println("receive-----------> "+new String(data,0,length));
            String retData = "hi" + new SimpleDateFormat().format(new Date());
            byte[] bytes = retData.getBytes(StandardCharsets.UTF_8);
            uSocket.send(new DatagramPacket(bytes,bytes.length,dataReceiveP.getAddress(),dataReceiveP.getPort()));
        }
    }
}
