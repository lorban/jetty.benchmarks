package org.eclipse.jetty.benchmark.operations;

public enum HttpResult
{
    HTTP_2xx,
    HTTP_4xx,
    HTTP_5xx,
    HTTP_OTHER,
    EXCEPTION,
    ;

    public static HttpResult from(int status)
    {
        switch (status / 100)
        {
            case 2:
                return HTTP_2xx;
            case 4:
                return HTTP_4xx;
            case 5:
                return HTTP_5xx;
            default:
                return HTTP_OTHER;
        }
    }
}
