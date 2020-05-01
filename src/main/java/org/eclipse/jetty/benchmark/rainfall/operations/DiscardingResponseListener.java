package org.eclipse.jetty.benchmark.rainfall.operations;

import java.util.concurrent.CountDownLatch;

import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;

class DiscardingResponseListener implements Response.Listener
{
    private final CountDownLatch latch = new CountDownLatch(1);
    private final int[] statusCode = new int[1];

    @Override
    public void onComplete(Result result)
    {
        int status = result.getResponse().getStatus();
        statusCode[0] = status;
        latch.countDown();
    }

    public int waitForResponse() throws InterruptedException
    {
        latch.await();
        return statusCode[0];
    }
}
