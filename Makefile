PKGS = \
    j/net/proxy 

# File name of final jar file
JAR_FILE_NAME = proxy.jar

# External libs will be combined together with the main jar
LIBS = libjava/libjava.jar

##### Do not edit beyond this line #####

CLASSES = $(addsuffix /*.class,$(addprefix $(OUT_DIR)/,$(PKGS)))

LIBS_OPTS = $(addprefix -cp ,$(LIBS))

LIBS_TARGETS = $(addprefix $(OUT_DIR)/,$(LIBS:.jar=.xxx))

OUT_DIR = bin

### targets
all: printFlags $(JAR_FILE_NAME)

### rules
printFlags:
	@mkdir -p "$(OUT_DIR)"

$(OUT_DIR)/%.xxx: %.jar  
	@mkdir -p "$(dir $(OUT_DIR)/$*.xxx)"
	@echo -n > "$@"
	cd "$(OUT_DIR)"; jar -xf "../$<"

$(OUT_DIR)/%.class: src/%.java
	javac $(LIBS_OPTS) -cp "$(OUT_DIR)" -source 1.5 -target 1.5 -d "$(OUT_DIR)" $? 
# $(?:src/%=%)

$(JAR_FILE_NAME): $(CLASSES) $(LIBS_TARGETS)
	jar -cfe "$@" j.net.proxy.ProxyServer -C "$(OUT_DIR)" .

clean:
	rm -rf "$(OUT_DIR)" "$(JAR_FILE_NAME)" *~ 

