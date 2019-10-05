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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.controller.HttpIntegrationMsg;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.custom.generator.MsgGenerator;
import org.thingsboard.integration.http.AbstractHttpIntegration;
import org.thingsboard.server.gen.transport.KeyValueProto;
import org.thingsboard.server.gen.transport.KeyValueType;
import org.thingsboard.server.gen.transport.PostAttributeMsg;
import org.thingsboard.server.gen.transport.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.TsKvListProto;

import java.util.Collections;
import java.util.List;

@Slf4j
public class CustomIntegration extends AbstractHttpIntegration<HttpIntegrationMsg> {

    private final ObjectMapper mapper = new ObjectMapper();
    private MsgGenerator generator;

    private boolean createEntityView;
    private List<String> keys;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        JsonNode configuration = mapper.readTree(params.getConfiguration().getConfiguration().get("configuration").asText());

        if (configuration.has("createEntityView")) {
            createEntityView = configuration.get("createEntityView").asBoolean();
        }
        if (configuration.has("entityViewKeys")) {
            keys = Collections.singletonList(configuration.get("entityViewKeys").asText());
        }

        long msgGenerationIntervalMs = 0L;
        if (configuration.has("msgGenerationIntervalMs")) {
            msgGenerationIntervalMs = configuration.get("msgGenerationIntervalMs").asLong();
        }

        if (configuration.has("url")) {
            generator = new MsgGenerator(configuration.get("url").asText(), msgGenerationIntervalMs);
            generator.startGenerator();
        }
    }

    @Override
    public void destroy() {
        generator.destroy();
    }

    @Override
    protected ResponseEntity doProcess(HttpIntegrationMsg msg) throws Exception {

        UplinkData.UplinkDataBuilder builder = UplinkData.builder();
        builder.deviceName("DeviceName");
        builder.deviceType("Type");
        builder.telemetry(PostTelemetryMsg.newBuilder().addTsKvList(TsKvListProto.newBuilder().setTs(System.currentTimeMillis()).addKv(KeyValueProto.newBuilder().setType(KeyValueType.STRING_V).setKey("key").setStringV("value").build()).build()).build());
        builder.attributesUpdate(PostAttributeMsg.newBuilder().getDefaultInstanceForType());

        UplinkData uplinkData = builder.build();
        processUplinkData(context, uplinkData);

        if (createEntityView) {
            createEntityView(context, uplinkData, uplinkData.getDeviceName() + "_View", uplinkData.getDeviceType(), keys);
        }

        return fromStatus(HttpStatus.OK);
    }

}
