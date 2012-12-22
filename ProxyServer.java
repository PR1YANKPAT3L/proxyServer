/**
 * A simple proxy server with the following characteristics:
 * - HTTP only.
 * - No keep-alive
 * - Opens a new connection to the target server every time a client
 *   connection requests for it.
 * - No caching.
 * - Supports UTF-8 URLs (not tested)
 * - Supports GET, POST and all other methods.
 * - Supports HTTP video streaming, for e.g., youtube.
 *
 * It is strictly just a middleman that captures the HTTP traffic
 * between the client and server.
 * @author Lucas Tan
 */

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;

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
        ServerSocket sock = new ServerSocket(port, 20);
       
        Queue<Handler> handlers = new LinkedList<Handler>();
        for (int i = 0; i < NUM_THREADS; i++)
        {
            Handler h = new Handler();
            handlers.offer(h);
            h.start();
        }

        while(true)
        {
            Socket clientSock = sock.accept();
            
            final InetAddress localAddress = clientSock.getLocalAddress();
            final int localPort = clientSock.getLocalPort();
            final InetAddress remoteAddress = clientSock.getInetAddress();
            final int remotePort = clientSock.getPort();

            System.out.println(
                "Accepted connection from "+
                remoteAddress.getCanonicalHostName() + ":" + 
                remotePort + " (" + remoteAddress.getHostAddress() + ")");
            
            Handler h = handlers.poll();
            h.post(clientSock);
            handlers.offer(h);
        }
    }

    private static void write(OutputStream bos,
        String s) throws Exception
    {
        byte[] b = s.getBytes("UTF-8");
        bos.write(b);
    }

    private static void close(Socket s)
    {
        try{ s.close(); }
        catch (Throwable t){ /*nothing*/ }
    }

    private static void close(OutputStream os)
    {
        try{ os.close(); }
        catch (Throwable t){ /*nothing*/ }
    }

    private static void close(Writer w)
    {
        try{ w.close(); }
        catch (Throwable t){ /*nothing*/ }
    }
    
    private static class Handler extends Thread
    {
        private static final byte[] NEW_LINE = {'\r', '\n'};
        
        private Writer bos = null;
        
        private final Queue<Socket> jobs;
        private final Semaphore jobsLock = new Semaphore(0, true);

        public Handler() throws Exception
        {
                bos = new OutputStreamWriter(
                        new BufferedOutputStream(
                            new FileOutputStream(
                                "proxy."+getId()+".log", true)), "UTF-8");

            jobs = new LinkedList<Socket>();
        }

        private static void error(Socket clientSock, byte[] body) 
            throws Exception
        {
            write(clientSock.getOutputStream(),
                "HTTP/1.1 400 Bad Request\r\n"+
                "Connection: close\r\n"+
                "Content-Type: text/plain; charset=UTF-8\r\n"+
                "Content-Length: "+body.length+"\r\n"+
                "\r\n");            
            
            clientSock.getOutputStream().write(body);
        }

        private static void error(Socket clientSock, String msg) 
            throws Exception
        {
            byte []b = msg.getBytes("UTF-8");
            error(clientSock, b);
        }

        @Override
        public void run() 
        {
            while(true)
            {
                this.jobsLock.acquireUninterruptibly();    

                Socket clientSock = null;
                synchronized(this.jobs)
                {
                    clientSock = this.jobs.poll();
                }
                    
                handleWrapper(clientSock);
                close(clientSock);
            }
        }

        public void post(Socket clientSock)
        {
            synchronized(this.jobs)
            {
                this.jobs.offer(clientSock);
            }
            
            this.jobsLock.release();
        }

        private void handleWrapper(Socket clientSock)
        {
            try
            {
                handle(clientSock);
            }
            catch (Throwable e)
            {
                // write exception to log file
                try
                {
                    PrintWriter pw = new PrintWriter(this.bos);
                    pw.println("----------------------------");
                    e.printStackTrace(pw);
                    pw.println("----------------------------");
                    pw.flush();
                    // don't close pw
                }
                catch (Exception ex){ /*nothing*/ }

                // write exception to client
                try
                {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(bout, "UTF-8"));
                    e.printStackTrace(pw);
                    close(pw);
                    error(clientSock, bout.toByteArray());
                }
                catch(Exception ex){/*nothing*/}
            }
            
        }

        private void handle(Socket clientSock)
            throws Exception
        {
            final OutputStream clientOutput = clientSock.getOutputStream();

            final LineInputStream clientLineInput = 
                new LineInputStream(clientSock.getInputStream());

            // First line is of the
            // form: GET http://domain.com:port/path/to?query=string HTTP/1.1
           
            // The absolute URI form is required.

            List<String> headers = new ArrayList<String>();

            // Read headers from client.
            int contentLen = 0;
            while (true)
            {
                final String line = clientLineInput.readLine().trim();
                
                final String CONNECTION = "connection:";
                if (! line.toLowerCase().startsWith(CONNECTION))
                {
                    headers.add(line);
                }
                
                if (line.isEmpty()) break;
                
                final String CONTENT_LENGTH = "content-length:";
                if (line.toLowerCase().startsWith(CONTENT_LENGTH))
                {
                    contentLen = Integer.parseInt(line.substring(CONTENT_LENGTH.length()));
                }
            }

            headers.add("Connection: close");

            final String toks[] = headers.get(0).split("\\s+");
            final URI uri = new URI(toks[1]);
            final int servPort = uri.getPort() <= 0 ? 80 : uri.getPort();
            
            final Socket servSock = new Socket(uri.getHost(), servPort);

            final LineInputStream servLineInput = new LineInputStream(servSock.getInputStream());
            final OutputStream servOutput = servSock.getOutputStream();

            // write headers to serv
            for(String line : headers)
            {
                write(servOutput, line);
                servOutput.write(NEW_LINE);
            }

            byte[] buf = new byte[8192*4];

            int readBodyLen = 0;
            String errorMsg = null;
            // read content body from client and write to serv
            while (readBodyLen < contentLen)
            {
                int read = clientLineInput.read(buf, 0, contentLen-readBodyLen);
                if (read < 0) { errorMsg = "Unexpected client close"; break; }
                readBodyLen += read;
                servOutput.write(buf, 0, read);
            }

            if (errorMsg != null)
            {
                error(clientSock, errorMsg);
                close(servSock);
                return;
            }

            // read headers from serv and write to client
            while (true)
            {
                final String line = servLineInput.readLine().trim();
                final String CONNECTION = "connection:";
                if (line.toLowerCase().startsWith(CONNECTION))
                {
                    continue;
                }

                if (line.isEmpty()) 
                {
                    write(clientOutput, "Connection: close");
                    clientOutput.write(NEW_LINE);
                    clientOutput.write(NEW_LINE);
                    break;
                }
                write(clientOutput, line);
                clientOutput.write(NEW_LINE);
            }

            // read content body from serv and write to client
            while(true)
            {
                int read = servLineInput.read(buf);
                if (read < 0) break;

                clientOutput.write(buf, 0, read);
            }
            
            close(servSock);
        }
    }
}


