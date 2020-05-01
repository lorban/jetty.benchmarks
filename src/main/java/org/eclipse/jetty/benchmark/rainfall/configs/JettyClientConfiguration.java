package org.eclipse.jetty.benchmark.rainfall.configs;

import java.util.Collections;
import java.util.List;

import io.rainfall.Configuration;
import org.eclipse.jetty.client.HttpClient;

public class JettyClientConfiguration extends Configuration
{
    private final HttpClient httpClient;

    public JettyClientConfiguration(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    public HttpClient getHttpClient()
    {
        return httpClient;
    }

    @Override
    public List<String> getDescription()
    {
        return Collections.singletonList("Jetty client");
    }
}
