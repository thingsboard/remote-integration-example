/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.integration.custom.basic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.UplinkContentType;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.api.data.UplinkMetaData;
import org.thingsboard.integration.custom.client.CustomClient;
import org.thingsboard.integration.custom.message.CustomIntegrationMsg;
import org.thingsboard.integration.custom.message.CustomResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CustomIntegration extends AbstractIntegration<CustomIntegrationMsg> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int bindPort = 5555;
    private static final long msgGenerationIntervalMs = 5000;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workGroup;
    private Channel serverChannel;
    private CustomClient client;
    private String deviceName;
    private boolean initialized;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        JsonNode configuration = mapper.readTree(params.getConfiguration().getConfiguration().get("configuration").asText());
        try {
            bossGroup = new NioEventLoopGroup();
            workGroup = new NioEventLoopGroup();
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new StringEncoder(), new StringDecoder(), new LineBasedFrameDecoder(1024));
                    socketChannel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                            log.debug("Server received the message: {}", msg);
                            if (msg.startsWith("Hello to ThingsBoard!")) {
                                deviceName = msg.substring(msg.indexOf("[") + 1, msg.indexOf("]"));
                                ctx.writeAndFlush("Hello from ThingsBoard!");
                                initialized = true;
                            } else {
                                if (initialized) {
                                    CustomResponse response = new CustomResponse();
                                    process(new CustomIntegrationMsg(msg, response));
                                    ctx.writeAndFlush(response.getResult());
                                } else {
                                    log.warn("The flaw was not started correctly!");
                                }
                            }
                        }
                    });
                }
            });
            int port = getBindPort(configuration);
            serverChannel = bootstrap.bind(port).sync().channel();
            client = new CustomClient(port, getMsgGeneratorIntervalMs(configuration));
        } catch (Exception e) {
            log.error("Failed to init TCP server!", e);
            throw new RuntimeException();
        }
    }

    protected UplinkContentType getUplinkContentType() {
        return UplinkContentType.TEXT;
    }

    @Override
    public void destroy() {
        client.destroy();
        try {
            serverChannel.close().sync();
        } catch (Exception e) {
            log.error("Failed to close the channel!", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }

    @Override
    public void process(CustomIntegrationMsg customIntegrationMsg) {
        CustomResponse response = customIntegrationMsg.getResponse();
        if (!this.configuration.isEnabled()) {
            response.setResult("Integration is disabled");
            return;
        }
        String status = "OK";
        Exception exception = null;
        try {
            response.setResult(doProcess(customIntegrationMsg.getMsg()));
            integrationStatistics.incMessagesProcessed();
        } catch (Exception e) {
            log.debug("Failed to apply data converter function: {}", e.getMessage(), e);
            exception = e;
            status = "ERROR";
            response.setResult(status);
        }
        if (status.equals("ERROR")) {
            integrationStatistics.incErrorsOccurred();
        }
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", getUplinkContentType(), customIntegrationMsg.getMsg(), status, exception);
            } catch (Exception e) {
                log.warn("Failed to persist debug message!", e);
            }
        }
    }

    private String doProcess(String msg) throws Exception {
        byte[] data = mapper.writeValueAsBytes(msg);
        Map<String, String> metadataMap = new HashMap<>(metadataTemplate.getKvMap());
        List<UplinkData> uplinkDataList = convertToUplinkDataList(context, data, new UplinkMetaData(getUplinkContentType(), metadataMap));
        if (uplinkDataList != null && !uplinkDataList.isEmpty()) {
            for (UplinkData uplinkData : uplinkDataList) {
                UplinkData uplinkDataResult = UplinkData.builder()
                        .deviceName(deviceName)
                        .deviceType(uplinkData.getDeviceType())
                        .telemetry(uplinkData.getTelemetry())
                        .attributesUpdate(uplinkData.getAttributesUpdate())
                        .customerName(uplinkData.getCustomerName())
                        .build();
                processUplinkData(context, uplinkDataResult);
            }
            return "OK";
        }
        return "No Content";
    }

    private int getBindPort(JsonNode configuration) {
        int port;
        if (configuration.has("port")) {
            port = configuration.get("port").asInt();
        } else {
            log.warn("Failed to find [port] field in integration config, default value [{}] is used!", bindPort);
            port = bindPort;
        }
        return port;
    }

    private long getMsgGeneratorIntervalMs(JsonNode configuration) {
        long msgIntervalMs;
        if (configuration.has("msgGenerationIntervalMs")) {
            msgIntervalMs = configuration.get("msgGenerationIntervalMs").asLong();
        } else {
            log.warn("Failed to find [msgGenerationIntervalMs] field in integration config, default value [{}] is used!", msgGenerationIntervalMs);
            msgIntervalMs = msgGenerationIntervalMs;
        }
        return msgIntervalMs;
    }

}
