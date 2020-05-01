package org.eclipse.jetty.benchmark.handlers;

import java.io.IOException;
import java.io.InputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class SyncConsumingHandler extends AbstractHandler
{
    private final byte[] answer;
    private final byte[] discardBuffer = new byte[512];

    public SyncConsumingHandler(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        discard(request.getInputStream());
        response.getOutputStream().write(answer);
        response.setStatus(200);
        baseRequest.setHandled(true);
    }

    private void discard(InputStream inputStream) throws IOException
    {
        while (true)
        {
            int read = inputStream.read(discardBuffer);
            if (read == -1)
                break;
        }
    }
}
