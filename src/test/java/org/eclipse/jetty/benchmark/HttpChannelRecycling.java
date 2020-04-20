package org.eclipse.jetty.benchmark;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.benchmark.handlers.AsyncHandler;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;
import org.terracotta.angela.client.Client;
import org.terracotta.angela.client.ClientArray;
import org.terracotta.angela.client.ClientArrayFuture;
import org.terracotta.angela.client.ClusterFactory;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.common.ToolExecutionResult;
import org.terracotta.angela.common.topology.ClientArrayTopology;

import static org.terracotta.angela.client.config.custom.CustomConfigurationContext.customConfigurationContext;
import static org.terracotta.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;

public class HttpChannelRecycling
{
    private static void runClient(int count, String urlAsString) throws Exception
    {
        long before = System.nanoTime();
        System.out.println("Running client; " + count + " requests...");
        HttpClient httpClient = new HttpClient(new HttpClientTransportOverHTTP2(new HTTP2Client()));
        httpClient.start();
        ExecutorService executorService = Executors.newFixedThreadPool(8);

        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            Future<Object> f = executorService.submit(() ->
            {
                ContentResponse response = httpClient.GET(urlAsString);
                return null;
            });
            futures.add(f);
        }

        for (Future<Object> future : futures)
        {
            future.get();
        }

        executorService.shutdownNow();
        httpClient.stop();
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - before);
        System.out.println("Stopped client; ran for " + elapsedSeconds + " seconds");
    }

    @Test
    void httpChannelRecycling() throws Exception
    {
        System.setProperty("angela.rootDir", "/work/angela");
        System.setProperty("angela.java.version", "1.11");

        ConfigurationContext configContext = customConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))));

        try (ClusterFactory factory = new ClusterFactory("AngelaTest::httpChannelRecycling", configContext))
        {
            ClientArray clientArray = factory.clientArray();
            Client client = clientArray.getClients().iterator().next();

            ClientArrayFuture caf = clientArray.executeOnAll((cluster ->
            {
                System.setProperty("jetty.debug.recycleHttpChannels", "false");
                Server server = new Server();
                HttpConfiguration httpConfig = new HttpConfiguration();
                ServerConnector serverConnector = new ServerConnector(server, new HTTP2CServerConnectionFactory(httpConfig));
                serverConnector.setPort(8080);
                server.addConnector(serverConnector);
                server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
                server.start();
            }));
            caf.get();

            System.out.println("Warming up...");
            runClient(1_500_000, "http://localhost:8080");

            ToolExecutionResult toolExecutionResult = clientArray.jcmd(client).executeCommand("JFR.start", "name=jetty-server", "settings=profile");
            if (toolExecutionResult.getExitStatus() != 0)
                throw new RuntimeException("JCMD failure: " + toolExecutionResult);

            System.out.println("Benchmarking...");
            runClient(3_000_000, "http://localhost:8080");

            toolExecutionResult = clientArray.jcmd(client).executeCommand("JFR.dump", "name=jetty-server", "filename=jetty-server.jfr");
            if (toolExecutionResult.getExitStatus() != 0)
                throw new RuntimeException("JCMD failure: " + toolExecutionResult);
            toolExecutionResult = clientArray.jcmd(client).executeCommand("JFR.stop", "name=jetty-server");
            if (toolExecutionResult.getExitStatus() != 0)
                throw new RuntimeException("JCMD failure: " + toolExecutionResult);

            client.browse(".").list().stream().filter(rf -> rf.getName().endsWith(".jfr")).forEach(rf ->
            {
                try
                {
                    File target = new File("./target/jfr");
                    target.mkdirs();
                    String localFilename = "jetty-server-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".jfr";
                    rf.downloadTo(new File(target, localFilename));
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
