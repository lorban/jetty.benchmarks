package org.eclipse.jetty.benchmark.rainfall.operations;

import io.rainfall.WeightedOperation;
import org.eclipse.jetty.client.api.ContentProvider;

public class HttpOperations
{
    public static GetOperation get(String url)
    {
        return new GetOperation(url);
    }

    public static WeightedOperation get(double weight, String url)
    {
        return new WeightedOperation(weight, new GetOperation(url));
    }

    public static PostOperation post(String url, ContentProvider contentProvider)
    {
        return new PostOperation(url, contentProvider);
    }

    public static WeightedOperation post(double weight, String url, ContentProvider contentProvider)
    {
        return new WeightedOperation(weight, new PostOperation(url, contentProvider));
    }
}
