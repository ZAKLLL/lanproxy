package org.fengfei.lanproxy.client.udp;

import lombok.Data;
import org.fengfei.lanproxy.protocol.ProxyMessage;

/**
 * @author jiakui_zhang
 * @version 1.0
 * @description TODO
 * @createTime 11/4/2022 2:22 PM
 */
@Data
class UdpConnectInfo {
    String userClientIp;
    Integer userClientPort;
    String targetServerIp;
    Integer targetServerPort;
    String userClientAddress;
    String targetServerAddress;

    public UdpConnectInfo(ProxyMessage udpConnectInfoMsg) {

        String requestInfo = new String(udpConnectInfoMsg.getData());
        final String[] ipInfos = requestInfo.split("-");
        final String[] userClientIpInfo = ipInfos[0].split(":");
        final String[] targetServerIpInfo = ipInfos[1].split(":");

        this.userClientIp = userClientIpInfo[0];
        this.userClientPort = Integer.parseInt(userClientIpInfo[1]);

        this.targetServerIp = targetServerIpInfo[0];
        this.targetServerPort = Integer.parseInt(targetServerIpInfo[1]);

        this.userClientAddress = ipInfos[0];
        this.targetServerAddress = ipInfos[1];
    }
}
