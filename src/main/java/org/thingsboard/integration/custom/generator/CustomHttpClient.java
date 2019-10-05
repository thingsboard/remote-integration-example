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
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;

import java.io.IOException;

@Slf4j
public class CustomHttpClient {

    private HttpClient client;

    public CustomHttpClient() {
        MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
        HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setDefaultMaxConnectionsPerHost(1000);
        connectionManager.setParams(params);
        this.client = new HttpClient(connectionManager);
    }

    public void sendData(String uri, String data) throws IOException {
        PostMethod post = new PostMethod(uri);
        post.setRequestEntity(new StringRequestEntity(data, "application/json", "UTF-8"));
        post.setRequestHeader("Accept", "application/json");
        post.setRequestHeader("Content-type", "application/json");
        try {
            client.executeMethod(post);
        } finally {
            post.releaseConnection();
        }
    }

}
