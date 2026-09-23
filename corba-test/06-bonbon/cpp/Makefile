CXX = g++
CXXFLAGS = -Isrc -Wall
LIBS = -lomniORB4 -lomnithread -lmysqlcppconn
BIN = bin/cpp_peer

SRCS = src/boutiqueSK.cc src/StockImpl.cc src/cpp_boutique.cc

all: $(BIN)

$(BIN): $(SRCS)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -o $(BIN) $(SRCS) $(LIBS)

clean:
	rm -f $(BIN)

.PHONY: all clean
