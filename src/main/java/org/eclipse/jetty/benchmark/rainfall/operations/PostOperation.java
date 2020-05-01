package org.eclipse.jetty.benchmark.rainfall.operations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.rainfall.AssertionEvaluator;
import io.rainfall.Configuration;
import io.rainfall.Operation;
import io.rainfall.statistics.StatisticsHolder;
import org.eclipse.jetty.benchmark.rainfall.configs.JettyClientConfiguration;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.http.HttpMethod;

import static org.eclipse.jetty.benchmark.rainfall.operations.HttpResult.EXCEPTION;

class PostOperation implements Operation
{
    private final String urlAsString;
    private final ContentProvider contentProvider;

    PostOperation(String urlAsString, ContentProvider contentProvider)
    {
        this.urlAsString = urlAsString;
        this.contentProvider = contentProvider;
    }

    @Override
    public void exec(StatisticsHolder statisticsHolder, Map<Class<? extends Configuration>, Configuration> map, List<AssertionEvaluator> list)
    {
        JettyClientConfiguration jettyClientConfiguration = (JettyClientConfiguration)map.get(JettyClientConfiguration.class);

        long start = System.nanoTime();
        HttpResult result;
        try
        {
            DiscardingResponseListener responseListener = new DiscardingResponseListener();
            jettyClientConfiguration.getHttpClient()
                .newRequest(urlAsString)
                .content(contentProvider)
                .method(HttpMethod.POST)
                .send(responseListener);
            result = HttpResult.from(responseListener.waitForResponse());
        }
        catch (Exception e)
        {
            result = EXCEPTION;
        }
        statisticsHolder.record("POST", (System.nanoTime() - start), result);
    }

    @Override
    public List<String> getDescription()
    {
        return Collections.singletonList("POST " + urlAsString + " of " + contentProvider);
    }
}
