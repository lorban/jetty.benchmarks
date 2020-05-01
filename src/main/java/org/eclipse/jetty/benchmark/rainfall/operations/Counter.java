package org.eclipse.jetty.benchmark.rainfall.operations;

import java.util.concurrent.atomic.AtomicInteger;

class Counter
{

    private final int requestsBetweenCloses;
    private final AtomicInteger requestCount = new AtomicInteger();

    public Counter(int requestsBetweenCloses)
    {
        this.requestsBetweenCloses = requestsBetweenCloses;
    }

    public boolean increment()
    {
        int count = requestCount.incrementAndGet();
        if (count >= requestsBetweenCloses)
        {
            requestCount.addAndGet(-requestsBetweenCloses);
            return true;
        }
        return false;
    }
}
