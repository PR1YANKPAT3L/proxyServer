PKGS = \
    j/net/proxy 

# File name of final jar file
JAR_FILE_NAME = proxy.jar

# External libs will be combined together with the main jar
LIBS = libjava/libjava.jar

MAIN_CLASS = j.net.proxy.ProxyServer

##### Do not edit beyond this line #####

include libjava/templ.mk

