A simple multi-threaded proxy server 

Features
========
- HTTP only.
- No keep-alive
- Opens a new connection to the target server every time a client
  connection requests for it.
- No caching.
- Supports UTF-8 URLs (not tested)
- Supports GET, POST and all other methods.
- Supports HTTP video streaming, for e.g., youtube.
- Supports logging of HTTP traffic from both client and server side.
- Supports reading of chunked transfer encoding. 
   In the log files, even though Transfer-Encoding might specify
   "chunked", the data logged would have been decoded for convenience.

Feel free to modify the code for your own use. 

But please acknowledge the original author. Thanks!

Building
=========
1. Build https://github.com/lucastan/libjava
2. Put libjava-xxx.jar into the proxyServer/lib dir (create it if necessary)
3. Do `git submodule init` and `git submodule update` in proxyServer dir.
3. Do `ant` in proxyServer dir.

Usage
=========
Command: `java -jar dist-1.0.0/proxy-1.0.0-all.jar [options]`
<pre>
options:
-t : Number of threads. Default is 3
-s : No logging. Default is to log
-n : Number of listen back log [Default=20]
-p : Listen port [Required]
</pre>

Log files will be created as proxy.{thread-id}.{client/serv}.log
For e.g., proxy.0.client.log stores all traffic received from the client.

The headers shown in the log files are in unmodified form, and might not
be the exact headers sent to the client/server.

