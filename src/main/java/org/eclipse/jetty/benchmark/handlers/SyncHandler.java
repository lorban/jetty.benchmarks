package org.eclipse.jetty.benchmark.handlers;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class SyncHandler extends AbstractHandler
{
    private final byte[] answer;

    public SyncHandler(byte[] answer)
    {
        this.answer = answer;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        response.setStatus(200);
        response.getOutputStream().write(answer);
    }
}
