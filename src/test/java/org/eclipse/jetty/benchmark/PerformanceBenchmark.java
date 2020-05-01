package org.eclipse.jetty.benchmark;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.rainfall.Runner;
import io.rainfall.Scenario;
import io.rainfall.reporting.HtmlReport;
import io.rainfall.reporting.PeriodicHlogReporter;
import org.eclipse.jetty.benchmark.handlers.SyncConsumingHandler;
import org.eclipse.jetty.benchmark.rainfall.configs.JettyClientConfiguration;
import org.eclipse.jetty.benchmark.rainfall.operations.HttpResult;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.terracotta.angela.client.Client;
import org.terracotta.angela.client.ClientArray;
import org.terracotta.angela.client.ClusterFactory;
import org.terracotta.angela.client.ClusterMonitor;
import org.terracotta.angela.client.config.ConfigurationContext;
import org.terracotta.angela.client.filesystem.RemoteFile;
import org.terracotta.angela.common.cluster.Barrier;
import org.terracotta.angela.common.topology.ClientArrayTopology;

import static io.rainfall.configuration.ConcurrencyConfig.concurrencyConfig;
import static io.rainfall.configuration.ReportingConfig.report;
import static io.rainfall.execution.Executions.during;
import static io.rainfall.execution.Executions.times;
import static io.rainfall.unit.TimeDivision.seconds;
import static org.eclipse.jetty.benchmark.rainfall.operations.HttpOperations.get;
import static org.eclipse.jetty.benchmark.rainfall.operations.HttpOperations.post;
import static org.terracotta.angela.client.config.custom.CustomMultiConfigurationContext.customMultiConfigurationContext;
import static org.terracotta.angela.common.clientconfig.ClientArrayConfig.newClientArrayConfig;
import static org.terracotta.angela.common.metrics.HardwareMetric.*;

public class PerformanceBenchmark
{
    public static final int CLIENT_COUNT = 3;
    public static final int THREAD_PER_CLIENT_COUNT = 2;
    public static final int WARMUP_OCCURRENCES = 100_000;
    public static final int BENCHMARK_DURATION_IN_SECONDS = 60;
    public static final boolean USE_HTTP2 = true;

    @BeforeAll
    public static void setUp()
    {
        System.setProperty("angela.rootDir", "/work/angela");
        System.setProperty("angela.java.version", "1.11");
    }

    @Test
    void mix() throws Exception
    {
        ConfigurationContext configurationContext = customMultiConfigurationContext()
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().host("localhost"))))
            .clientArray(clientArray -> clientArray.clientArrayTopology(new ClientArrayTopology(newClientArrayConfig().hostSerie(CLIENT_COUNT, "localhost"))))
            .monitoring(monitoring -> monitoring.commands(EnumSet.of(CPU, MEMORY, NETWORK)));

        try (ClusterFactory factory = new ClusterFactory("RainfallPerformance::mix", configurationContext))
        {
            ClientArray serverClientArray = factory.clientArray();
            ClientArray clientClientArray = factory.clientArray();
            ClusterMonitor monitoring = factory.monitor();

            serverClientArray.executeOnAll(cluster ->
            {
                Server server = new Server();
                HttpConfiguration httpConfig = new HttpConfiguration();
                ServerConnector serverConnector = new ServerConnector(server, new HttpConnectionFactory(), new HTTP2CServerConnectionFactory(httpConfig));
                serverConnector.setPort(8080);
                server.addConnector(serverConnector);
                server.setHandler(new SyncConsumingHandler("Hi there!".getBytes(StandardCharsets.ISO_8859_1)));
                server.start();
            }).get();

            monitoring.startOnAll();
            System.out.println("Benchmarking...");
            clientClientArray.executeOnAll(cluster ->
            {
                BytesContentProvider contentProvider = new BytesContentProvider(new byte[512]);
                Scenario scenario = Scenario.scenario("Mix")
                    .exec(
                        get(.8, "http://localhost:8080"),
                        post(.2, "http://localhost:8080", contentProvider)
                    );
                HttpClient httpClient = USE_HTTP2 ?
                    new HttpClient(new HttpClientTransportOverHTTP2(new HTTP2Client()), new SslContextFactory.Client()) :
                    new HttpClient(new HttpClientTransportOverHTTP(), new SslContextFactory.Client());
                httpClient.start();

                Barrier barrier = cluster.barrier("start-mark", CLIENT_COUNT);
                int clientId = barrier.await();
                System.out.println("Client " + clientId + " started, waiting for other clients...");
                warmupClient(scenario, httpClient);

                System.out.println("Client " + clientId + " warmed up, waiting for other clients before starting benchmark...");
                barrier.await();
                benchmarkClient(scenario, httpClient);

                httpClient.stop();
                System.out.println("Client " + clientId + " done benchmarking");
            }).get();

            monitoring.stopOnAll();
            System.out.println("Collecting reports...");
            String targetRoot = "./target/reports/" + new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
            downloadAndAggregateReports(targetRoot + "/histograms", clientClientArray);
            monitoring.downloadTo(new File(targetRoot, "monitoring"));
        }
    }

    private void downloadAndAggregateReports(String targetRoot, ClientArray clientArray)
    {
        for (Client client : clientArray.getClients())
        {
            Predicate<RemoteFile> filter = rf -> rf.isFolder() && rf.getName().equals("rainfall-histo");
            client.browse(".").list().stream().filter(filter).forEach(rf ->
            {
                try
                {
                    File target = new File(targetRoot, client.getSymbolicName());
                    if (!target.mkdirs())
                        throw new IOException("Error creating folders: " + target);
                    rf.downloadTo(target);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
        HtmlReport.aggregateInPlace(EnumSet.allOf(HttpResult.class).toArray(new Enum[0]),
            clientArray.getClients().stream().map(Client::getSymbolicName).collect(Collectors.toList()),
            new File(targetRoot));
    }

    private void warmupClient(Scenario scenario, HttpClient httpClient) throws Exception
    {
        Runner.setUp(scenario)
            .executed(times(WARMUP_OCCURRENCES))
            .config(new JettyClientConfiguration(httpClient), concurrencyConfig().threads(THREAD_PER_CLIENT_COUNT),
                report(HttpResult.class))
            .start();
    }

    private void benchmarkClient(Scenario scenario, HttpClient httpClient) throws Exception
    {
        Runner.setUp(scenario)
            .executed(during(BENCHMARK_DURATION_IN_SECONDS, seconds))
            .config(new JettyClientConfiguration(httpClient), concurrencyConfig().threads(THREAD_PER_CLIENT_COUNT),
                report(HttpResult.class).log(new PeriodicHlogReporter<>("rainfall-histo")))
            .start();
    }
}
