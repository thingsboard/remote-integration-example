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
package org.thingsboard.integration.custom.generator;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MsgGenerator {

    private static final long initTimeoutMs = 10000;

    private final CustomHttpClient client;
    private final String url;
    private final long msgGenerationIntervalMs;
    private final ScheduledExecutorService scheduledExecutorService;

    public MsgGenerator(String url, long msgGenerationIntervalMs) {
        this.client = new CustomHttpClient();
        this.url = url;
        this.msgGenerationIntervalMs = msgGenerationIntervalMs;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void destroy() {
        if (this.scheduledExecutorService != null) {
            this.scheduledExecutorService.shutdownNow();
        }
    }

    public void startGenerator() {
        String data = "{}";
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                client.sendData(url, data);
            } catch (Exception e) {
                log.error("Failed to send data {}", data, e);
            }
        }, initTimeoutMs, msgGenerationIntervalMs, TimeUnit.MILLISECONDS);
    }

}
