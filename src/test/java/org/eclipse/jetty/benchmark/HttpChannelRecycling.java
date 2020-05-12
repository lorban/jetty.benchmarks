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
import java.util.function.Predicate;

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
import org.terracotta.angela.client.ClusterFactory;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.client.filesystem.RemoteFile;
import org.terracotta.angela.common.ToolExecutionResult;
import org.terracotta.angela.common.topology.ClientArrayTopology;

import static org.terracotta.angela.client.config.custom.CustomMultiConfigurationContext.customMultiConfigurationContext;
import static org.terracotta.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;

public class HttpChannelRecycling
{

    @Test
    void httpChannelRecycling() throws Exception
    {
        System.setProperty("angela.rootDir", "/work/angela");
        System.setProperty("angela.java.version", "1.11");

        ConfigurationContext configContext = customMultiConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))))
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))))
            ;

        try (ClusterFactory factory = new ClusterFactory("AngelaTest::httpChannelRecycling", configContext))
        {
            ClientArray serverClientArray = factory.clientArray();
            ClientArray clientClientArray = factory.clientArray();

            serverClientArray.executeOnAll(cluster ->
            {
                Server server = new Server();
                HttpConfiguration httpConfig = new HttpConfiguration();
                ServerConnector serverConnector = new ServerConnector(server, new HTTP2CServerConnectionFactory(httpConfig));
                serverConnector.setPort(8080);
                server.addConnector(serverConnector);
                server.setHandler(new AsyncHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
                server.start();
            }).get();

            System.out.println("Warming up...");
            clientClientArray.executeOnAll(cluster -> runClient(1_500_000, "http://localhost:8080")).get();

            System.out.println("Benchmarking...");
            jcmd(serverClientArray, "JFR.start", "name=jetty-server", "settings=profile");
            jcmd(clientClientArray, "JFR.start", "name=jetty-client", "settings=profile");
            clientClientArray.executeOnAll(cluster -> runClient(3_000_000, "http://localhost:8080")).get();

            System.out.println("Collecting flight recordings...");
            jcmd(serverClientArray, "JFR.dump", "name=jetty-server", "filename=jetty-server.jfr");
            jcmd(serverClientArray, "JFR.stop", "name=jetty-server");
            downloadFlightRecordings(serverClientArray, "jetty-server.jfr");
            jcmd(clientClientArray, "JFR.dump", "name=jetty-client", "filename=jetty-client.jfr");
            jcmd(clientClientArray, "JFR.stop", "name=jetty-client");
            downloadFlightRecordings(clientClientArray, "jetty-client.jfr");
        }
    }

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

    private void jcmd(ClientArray clientArray, String... arguments)
    {
        for (Client client : clientArray.getClients())
        {
            ToolExecutionResult toolExecutionResult = clientArray.jcmd(client).executeCommand(arguments);
            if (toolExecutionResult.getExitStatus() != 0)
                throw new RuntimeException("JCMD failure: " + toolExecutionResult);
        }
    }

    private void downloadFlightRecordings(ClientArray clientArray, String remoteFilename)
    {
        for (Client client : clientArray.getClients())
        {
            Predicate<RemoteFile> filter = rf -> rf.getName().equals(remoteFilename);
            client.browse(".").list().stream().filter(filter).forEach(rf ->
            {
                try
                {
                    File target = new File("./target/jfr");
                    target.mkdirs();
                    String localFilename = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss_").format(new Date()) + remoteFilename;
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
