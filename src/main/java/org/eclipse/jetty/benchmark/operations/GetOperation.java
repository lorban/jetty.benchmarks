package org.eclipse.jetty.benchmark.operations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.rainfall.AssertionEvaluator;
import io.rainfall.Configuration;
import io.rainfall.Operation;
import io.rainfall.TestException;
import io.rainfall.statistics.StatisticsHolder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;

import static org.eclipse.jetty.benchmark.operations.HttpResult.EXCEPTION;

public class GetOperation implements Operation
{
    private final String urlAsString;

    public GetOperation(String urlAsString)
    {
        this.urlAsString = urlAsString;
    }

    @Override
    public void exec(StatisticsHolder statisticsHolder, Map<Class<? extends Configuration>, Configuration> map, List<AssertionEvaluator> list) throws TestException
    {
        JettyClientConfiguration configuration = (JettyClientConfiguration)map.get(JettyClientConfiguration.class);
        HttpClient httpClient = configuration.getHttpClient();

        long start = System.nanoTime();
        try
        {
            ContentResponse response = httpClient.GET(urlAsString);
            statisticsHolder.record("http", (System.nanoTime() - start), HttpResult.from(response.getStatus()));
        }
        catch (Exception e)
        {
            statisticsHolder.record("http", (System.nanoTime() - start), EXCEPTION);
        }
    }

    @Override
    public List<String> getDescription()
    {
        return Collections.singletonList("GET");
    }
}
