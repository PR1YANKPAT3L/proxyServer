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
    private static final int DEFAULT_NUM_THREADS = 3;
    private static final boolean DEFAULT_LOGGING = true;

    private static class Options
    {
        public int numThreads = DEFAULT_NUM_THREADS;
        public boolean logging = DEFAULT_LOGGING;
        public int port = -1;
        public boolean ok = false;

        public static void printUsage()
        {
            System.err.println(
            "Usage: java ProxyServer <port> [options]\n"+
            "options:\n"+
            "-t : Number of threads\n"+
            "-s : No logging\n"
            );
        }

        public Options(String[] args)
        {
            try
            {
                parse(args);

                if (port <= 0)
                    throw new Exception("Please specify the listen port");

                if (numThreads <= 0)
                    throw new Exception
                        ("Please specify positive number of threads");

                ok = true;
            }
            catch (Exception e)
            {
                System.err.println("error parsing options: "+e.getMessage());
                
                printUsage();
            }
        }

        private void parse(String[] args) throws Exception
        {
            for (int i = 0; i < args.length; i++)
            {
                String a = args[i];
                String next = i != args.length - 1 ? args[i+1] : null;

                if(a.startsWith("-") && a.length() >= 2)
                {
                    char c = a.charAt(1);
                    switch(c)
                    {
                    case 't':
                        if (next == null)
                            throw new Exception("no. of threads not specified");
                        numThreads = Integer.parseInt(next);
                        i++;
                        break;

                    case 's':
                        logging = false;
                        break;
                    }
                }
                else
                {
                    port = Integer.parseInt(a);
                }
            }
        }
    }

    public static void main(String args[])
        throws Exception
    {
        Options opts = new Options(args);
        if (! opts.ok) return;

        // 20 max connections
        final ServerSocket sock = new ServerSocket(opts.port, 20);
       
        final List<ClientHandler> handlers = new ArrayList<ClientHandler>();
        for (int i = 0; i < opts.numThreads; i++)
        {
            OutputStream bosClient = new NullOutputStream();
            OutputStream bosServ = bosClient;

            if (opts.logging)
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

