CXX = g++
CXXFLAGS = -Isrc -Wall
LIBS = -lomniORB4 -lomnithread -lmysqlcppconn
BIN = bin/cpp_peer

SRCS = src/modeleSK.cc src/ServiceBaseImpl.cc src/cpp_peer.cc

all: $(BIN)

$(BIN): $(SRCS) src/ServiceBaseImpl.hh
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -o $(BIN) $(SRCS) $(LIBS)

clean:
	rm -f $(BIN)

.PHONY: all clean
