/**
 * A proxy server thread that handles one client connection.
 * @author Lucas Tan
 */

package j.net.proxy;

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;
import j.io.*;

public class ClientHandler extends Thread
{
    private static class HeaderData
    {
        public String httpVersion;
        public boolean chunked;
        public int contentLen;
        public List<String> headers;
    }

    private static class Request extends HeaderData
    {
        public URI uri;
        public String method;
        public int servPort;
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

    private static final byte[] NEW_LINE = {'\r', '\n'};
    
    private static final String SEP = 
        "\n------------------- %8d ---------------------\n";

    private final Queue<Socket> jobs;
    private final Semaphore jobsLock = new Semaphore(0, true);
    private final OutputStream clientLog;
    private final OutputStream servLog;

    public ClientHandler(
        OutputStream clientLog,
        OutputStream servLog) throws Exception
    {
        if (clientLog == null) 
            throw new IllegalArgumentException("clientLog is null");
        
        if (servLog == null) 
            throw new IllegalArgumentException("servLog is null");

        this.servLog = servLog;
        this.clientLog = clientLog;
        this.jobs = new LinkedList<Socket>();
    }

    private void logServ(byte[] b) throws IOException
    {
        logServ(b, 0, b.length);
    }

    private void logServ(String s) throws IOException
    {
        logServ(s.getBytes("UTF-8"));
    }

    private void logServ(byte [] b, int off, int len)
        throws IOException
    {
        this.servLog.write(b, off, len);
        this.servLog.flush();
    }
    
    private void logClient(byte[] b) throws IOException
    {
        logClient(b, 0, b.length);
    }

    private void logClient(String s) throws IOException
    {
        logClient(s.getBytes("UTF-8"));
    }
    
    private void logClient(byte [] b, int off, int len)
        throws IOException
    {
        this.clientLog.write(b, off, len);
        this.clientLog.flush();
    }

    private static void error(Socket clientSock, byte[] body) 
        throws Exception
    {
        final String response =
            "HTTP/1.1 400 Bad Request\r\n"+
            "Connection: close\r\n"+
            "Proxy-Connection: close\r\n"+
            "Content-Type: text/plain; charset=UTF-8\r\n"+
            "Content-Length: "+body.length+"\r\n"+
            "\r\n";

        write(clientSock.getOutputStream(), response);
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
        int num = 0;
        
        while(true)
        {
            this.jobsLock.acquireUninterruptibly();    

            Socket clientSock = null;
            synchronized(this.jobs)
            {
                clientSock = this.jobs.poll();
            }
                
            handleWrapper(num, clientSock);
            close(clientSock);
            num = num+1;
        }
    }

    public void post(Socket clientSock)
    {
        if (clientSock == null) 
            throw new IllegalArgumentException("clientSock is null");
        
        synchronized(this.jobs)
        {
            this.jobs.offer(clientSock);
        }
        
        this.jobsLock.release();
    }

    private void handleWrapper(int num, Socket clientSock)
    {
        Socket servSock = null;

        try
        {
            final LineInputStream clientLineInput = 
                new LineInputStream(clientSock.getInputStream());
            
            List<String> headers = readHeaders(clientLineInput);
            Request r = createRequest(headers);

            servSock = new Socket(r.uri.getHost(), r.servPort);
            
            handle(num, clientSock, clientLineInput, servSock, r, headers);
        }
        catch (Throwable e)
        {
            // write exception to log file
            try
            {
                PrintWriter pw = new PrintWriter(this.clientLog);
                logClient(String.format(SEP, num));
                e.printStackTrace(pw);
                this.clientLog.flush();
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
        finally
        {
            close(servSock);
        }
    }

    private static List<String> readHeaders(LineInputStream lis)
        throws IOException
    {
        List<String> headers = new ArrayList<String>();
        
        while (true)
        {
            final String line = lis.readLine().trim();
            if (line.isEmpty()) break;
            headers.add(line);
        }

        return Collections.unmodifiableList(headers);
    }

    private static void populateHeaderData
        (List<String> headers, HeaderData data)
    {
        final String CONTENT_LENGTH = "content-length:";
        final String TRANSFER_ENCODING = "transfer-encoding:";
        final String CONNECTION = "connection:";
        final String PROXY_CONNECTION = "proxy-connection:";
        
        data.contentLen = -1;
        data.chunked = false;
        data.headers = new ArrayList<String>();

        for (String line : headers)
        {
            final String lineLower = line.toLowerCase();
            boolean isChunkedHeader = false;

            if (lineLower.startsWith(CONTENT_LENGTH))
            {
                data.contentLen = Integer.parseInt(line.substring(CONTENT_LENGTH.length()).trim());
            }
            else if (lineLower.startsWith(TRANSFER_ENCODING))
            {
                if (lineLower.indexOf("chunked") >= 0)
                {
                    data.chunked = true;
                    isChunkedHeader = true;
                }
            }
        
            if (! isChunkedHeader &&
                ! lineLower.startsWith(CONNECTION)
             && ! lineLower.startsWith(PROXY_CONNECTION))
            {
                data.headers.add(line);
            }
        }
        
        data.headers.add("Connection: close");
    }

    private static HeaderData createHeaderData(List<String> headers)
    {
        HeaderData data = new HeaderData();
        populateHeaderData(headers, data);
        return data;
    }
    
    private static Request createRequest(List<String> headers)
        throws Exception
    {
        Request data = new Request();
        
        populateHeaderData(headers, data);

        // First line is of the
        // form: GET http://domain.com:port/path/to?query=string HTTP/1.1
        // The absolute URI form is required.
        final String toks[] = headers.get(0).split("\\s+");
        data.method = toks[0];
        data.httpVersion = toks[2];
        data.uri = new URI(toks[1]);
        data.servPort = data.uri.getPort() <= 0 ? 80 : data.uri.getPort();

        return data;
    }

    private void handle(
        int num,
        Socket clientSock, 
        LineInputStream clientLineInput, 
        Socket servSock, 
        Request data,
        List<String> headers)
        throws Exception
    {
        final OutputStream clientOutput = clientSock.getOutputStream();

        final LineInputStream servLineInput = 
            new LineInputStream(servSock.getInputStream());
        final OutputStream servOutput = servSock.getOutputStream();

        
        // write filtered headers from client to serv
        for(String line : data.headers)
        {
            write(servOutput, line);
            servOutput.write(NEW_LINE);
        }

        servOutput.write(NEW_LINE);
        
        logClient(String.format(SEP, num));
        
        // write original headers to log
        for (String line : headers)
        {
            logClient(line);
            logClient(NEW_LINE);
        }

        logClient(NEW_LINE);

        InputStream clientBodyInput = null;
        if (data.chunked)
            clientBodyInput = new ChunkedInputStream(clientLineInput);
        else if (data.contentLen >= 0)
            clientBodyInput = new FixedInputStream(clientLineInput, data.contentLen);

        byte[] buf = new byte[8192*2];
        
        // read from client body, write to serv
        if (clientBodyInput != null)
        {
            while (true)
            {
                int read = clientBodyInput.read(buf);
                if (read < 0) break;

                servOutput.write(buf, 0, read);
                logClient(buf, 0, read);
            }
        }

        List<String> servHeaders = readHeaders(servLineInput);
        HeaderData servData = createHeaderData(servHeaders);
       
        servData.headers.add("Proxy-Connection: close");

        // write filtered headers to client
        for (String line : servData.headers)
        {
            write(clientOutput, line);
            clientOutput.write(NEW_LINE);
        }

        clientOutput.write(NEW_LINE);

        // write original headers to log
        logServ(String.format(SEP, num));
        for (String line : servHeaders)
        {
            logServ(line);
            logServ(NEW_LINE);
        }

        logServ(NEW_LINE);


        InputStream servBodyInput = null;
        if (servData.chunked)
            servBodyInput = new ChunkedInputStream(servLineInput);
        else if (data.contentLen >= 0)
            servBodyInput = new FixedInputStream(servLineInput, servData.contentLen);
        else // server should specify content length,
             // but we shall be tolerant here...
            servBodyInput = servLineInput;

        // read from serv body, write to client
        while (true)
        {
            int read = servBodyInput.read(buf);
            if (read < 0) break;
            clientOutput.write(buf, 0, read);
            logServ(buf, 0, read);
        }
    }
}

