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
package org.thingsboard.integration.custom.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CustomClient {

    private final ScheduledExecutorService scheduledExecutorService;
    private final NioEventLoopGroup workGroup;
    private final Random random;
    private final long msgGenerationIntervalMs;

    private ChannelFuture channelFuture;

    public CustomClient(long msgGenerationIntervalMs) {
        this.msgGenerationIntervalMs = msgGenerationIntervalMs;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.workGroup = new NioEventLoopGroup();
        this.random = new Random();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(this.workGroup);
            bootstrap.channel(NioSocketChannel.class);
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000); // not working...
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new StringEncoder(), new StringDecoder(), new LineBasedFrameDecoder(1024));
                    socketChannel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                            log.info("Client received the message: {}", msg);
                            if (msg.equals("Hello from ThingsBoard!")) {
                                log.info("Starting generator...");
                                startGenerator();
                            }
                        }
                    });
                }
            });
            channelFuture = bootstrap.connect("localhost", 5555).sync();
            channelFuture.channel().writeAndFlush("Hello to ThingsBoard! My name is [Device B]");
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("Failed to init TCP client!", e);
        }
    }

    private void startGenerator() {
        this.scheduledExecutorService.scheduleAtFixedRate(() ->
                channelFuture.channel().writeAndFlush(generateData()), 0, this.msgGenerationIntervalMs, TimeUnit.MILLISECONDS);
    }

    private String generateData() {
        int firstV = generateValue(15, 40);
        int secondV = generateValue(0, 100);
        int thirdV = generateValue(0, 100);
        return firstV + "," + secondV + "," + thirdV;
    }

    private int generateValue(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("Max value must be greater than min value!");
        }
        return random.nextInt((max - min) + 1) + min;
    }

    public void destroy() {
        if (this.scheduledExecutorService != null) {
            this.scheduledExecutorService.shutdownNow();
        }
        if (this.workGroup != null) {
            this.workGroup.shutdownGracefully();
        }
    }

}
