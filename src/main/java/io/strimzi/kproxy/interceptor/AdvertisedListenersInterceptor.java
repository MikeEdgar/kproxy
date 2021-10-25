/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.strimzi.kproxy.interceptor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.strimzi.kproxy.codec.DecodedResponseFrame;
import org.apache.kafka.common.message.MetadataResponseData;
import org.apache.kafka.common.protocol.ApiKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdvertisedListenersInterceptor implements Interceptor {

    private static final Logger LOGGER = LogManager.getLogger(AdvertisedListenersInterceptor.class);

    public interface AddressMapping {
        String host(String host, int port);
        int port(String host, int port);
    }

    private final AddressMapping mapping;

    public AdvertisedListenersInterceptor(AddressMapping mapping) {
        this.mapping = mapping;
    }

    @Override
    public boolean shouldDecodeRequest(ApiKeys apiKey, int apiVersion) {
        return false;
    }

    @Override
    public boolean shouldDecodeResponse(ApiKeys apiKey, int apiVersion) {
        return apiKey == ApiKeys.METADATA;
    }

    @Override
    public ChannelInboundHandler frontendHandler() {
        return null;
    }

    @Override
    public ChannelInboundHandler backendHandler() {
        return new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof DecodedResponseFrame && ((DecodedResponseFrame) msg).apiKey() == ApiKeys.METADATA) {
                    var resp = (MetadataResponseData) ((DecodedResponseFrame) msg).body();
                    for (var broker : resp.brokers()) {
                        String host = mapping.host(broker.host(), broker.port());
                        int port = mapping.port(broker.host(), broker.port());
                        LOGGER.trace("{}: Rewriting metadata response {}:{} -> {}:{}", ctx.channel(), broker.host(), broker.port(), host, port);
                        broker.setHost(host);
                        broker.setPort(port);
                    }
                }
                super.channelRead(ctx, msg);
            }
        };
    }

}
