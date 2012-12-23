/**
 * It is strictly just a middleman that captures the HTTP traffic
 * between the client and server.
 * @author Lucas Tan
 */

import java.io.*;
import java.util.*;
import java.net.*;

/**
 * Main application.
 */
public class ProxyServer
{
    private static class Options
    {
        // default values are specified in field declarations

        @Option(name="t", required=false, 
            description="Number of threads")
        @OptionConstraintRange(min=1, max=20)
        public int numThreads = 3;

        @Option(name="s", required=false,
            description="Specify to disable logging")
        public boolean noLog = false;

        @Option(name="p", required=true,
            description="Listen port")
        @OptionConstraintRange(min=1, max=65535)
        public int port;

        @Option(name="n", required=false,
            description="Number of listen back log")
        @OptionConstraintRange(min=1, max=100)
        public int maxBacklog = 20;
    }

    public static void main(String args[])
        throws Exception
    {
        Options opts = new Options();
        String[] extras = OptionParser.parse(opts, args);
        if (extras == null) return;

        final ServerSocket sock = 
            new ServerSocket(opts.port, opts.maxBacklog);
       
        final List<ClientHandler> handlers = new ArrayList<ClientHandler>();
        for (int i = 0; i < opts.numThreads; i++)
        {
            OutputStream bosClient = new NullOutputStream();
            OutputStream bosServ = bosClient;

            if (! opts.noLog)
            {
                bosClient = new BufferedOutputStream(
                                new FileOutputStream(
                                    "proxy."+i+".client.log", true));
                bosServ = new BufferedOutputStream(
                                new FileOutputStream(
                                    "proxy."+i+".serv.log", true));
            }

            ClientHandler h = new ClientHandler(bosClient, bosServ);
            handlers.add(h);
            h.start();
        }

        int idx = 0;
        while(true)
        {
            final Socket clientSock = sock.accept();
            
            final InetAddress localAddress = clientSock.getLocalAddress();
            final int localPort = clientSock.getLocalPort();
            final InetAddress remoteAddress = clientSock.getInetAddress();
            final int remotePort = clientSock.getPort();

            System.out.println(
                "Accepted connection from "+
                remoteAddress.getCanonicalHostName() + ":" + 
                remotePort + " (" + remoteAddress.getHostAddress() + ")");
            
            // round robin
            ClientHandler h = handlers.get(idx);
            h.post(clientSock);
            idx = (idx + 1) % handlers.size();
        }
    }
}

