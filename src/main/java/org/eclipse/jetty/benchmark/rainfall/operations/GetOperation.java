package org.eclipse.jetty.benchmark.rainfall.operations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.rainfall.AssertionEvaluator;
import io.rainfall.Configuration;
import io.rainfall.Operation;
import io.rainfall.statistics.StatisticsHolder;
import org.eclipse.jetty.benchmark.rainfall.configs.JettyClientConfiguration;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;

import static org.eclipse.jetty.benchmark.rainfall.operations.HttpResult.EXCEPTION;

class GetOperation implements Operation
{
    private final Counter closeCounter = new Counter(10);
    private final String urlAsString;

    GetOperation(String urlAsString)
    {
        this.urlAsString = urlAsString;
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
            Request request = jettyClientConfiguration.getHttpClient()
                .newRequest(urlAsString)
                .method(HttpMethod.GET);
            if (closeCounter.increment())
                request.header("Connection", "close");
            request.send(responseListener);
            result = HttpResult.from(responseListener.waitForResponse());
        }
        catch (Exception e)
        {
            result = EXCEPTION;
        }
        statisticsHolder.record("GET", (System.nanoTime() - start), result);
    }

    @Override
    public List<String> getDescription()
    {
        return Collections.singletonList("GET " + urlAsString);
    }
}
