package org.eclipse.jetty.benchmark.operations;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.rainfall.Configuration;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;

public class JettyClientConfiguration extends Configuration implements Closeable
{
    private final HttpClient httpClient;

    public JettyClientConfiguration() throws Exception
    {
        httpClient = new HttpClient(new HttpClientTransportOverHTTP2(new HTTP2Client()), null);
        httpClient.start();
    }

    public HttpClient getHttpClient()
    {
        return httpClient;
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            httpClient.stop();
        }
        catch (Exception e)
        {
            throw new IOException(e);
        }
    }

    @Override
    public List<String> getDescription()
    {
        return Collections.singletonList("JettyClientConfiguration");
    }
}
