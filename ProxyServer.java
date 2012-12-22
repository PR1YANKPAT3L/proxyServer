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
    private static final int NUM_THREADS = 3;

    public static void main(String args[])
        throws Exception
    {
        if (args.length != 1)
        {
            System.err.println("Usage: java ProxyServer <port>");
            return;
        }

        final int port = Integer.parseInt(args[0]);

        // 20 max connections
        final ServerSocket sock = new ServerSocket(port, 20);
       
        final List<ClientHandler> handlers = new ArrayList<ClientHandler>();
        for (int i = 0; i < NUM_THREADS; i++)
        {
            OutputStream bosClient = new BufferedOutputStream(
                                new FileOutputStream(
                                    "proxy."+i+".client.log", true));
            OutputStream bosServ = new BufferedOutputStream(
                                new FileOutputStream(
                                    "proxy."+i+".serv.log", true));
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
            
            ClientHandler h = handlers.get(idx);
            h.post(clientSock);
            idx = (idx + 1) % handlers.size();
        }
    }
}

