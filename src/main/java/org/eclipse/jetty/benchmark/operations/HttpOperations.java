package org.eclipse.jetty.benchmark.operations;

public class HttpOperations
{

    public static GetOperation get(String url)
    {
        return new GetOperation(url);
    }

}
