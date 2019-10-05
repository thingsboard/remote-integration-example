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
package org.thingsboard.integration.custom.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.integration.api.ThingsboardPlatformIntegration;
import org.thingsboard.integration.api.controller.BaseIntegrationController;
import org.thingsboard.integration.api.controller.HttpIntegrationMsg;
import org.thingsboard.server.common.data.integration.IntegrationType;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations/custom")
@Slf4j
public class CustomIntegrationController extends BaseIntegrationController {

    @SuppressWarnings("rawtypes")
    @ResponseStatus(value = HttpStatus.OK)
    @RequestMapping(value = "/", method = {RequestMethod.POST})
    public DeferredResult<ResponseEntity> processRequest(
            @RequestBody JsonNode msg,
            @RequestHeader Map<String, String> requestHeaders) {
        log.debug("Received request: {}", msg);
        DeferredResult<ResponseEntity> result = new DeferredResult<>();

        ListenableFuture<ThingsboardPlatformIntegration> integrationFuture = api.getIntegrationByRoutingKey(IntegrationType.CUSTOM.name());

        DonAsynchron.withCallback(integrationFuture, integration -> {
            if (integration == null) {
                result.setResult(new ResponseEntity<>(HttpStatus.NOT_FOUND));
                return;
            }
            if (integration.getConfiguration().getType() != IntegrationType.CUSTOM) {
                result.setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
                return;
            }
            api.process(integration, new HttpIntegrationMsg(requestHeaders, msg, result));
        }, failure -> {
            log.trace("Failed to fetch integration by routing key", failure);
            result.setResult(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }, api.getCallbackExecutor());

        return result;
    }

}
