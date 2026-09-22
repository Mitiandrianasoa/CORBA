CXX = g++
CXXFLAGS = -Isrc -Wall -std=c++14
LIBS = -lomniORB4 -lomnithread -lmysqlcppconn
BIN = bin/cpp_server

SRCS = src/ecoleSK.cc src/GestionEtudiantsImpl.cc src/cpp_server.cc

all: $(BIN)

$(BIN): $(SRCS)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) -o $(BIN) $(SRCS) $(LIBS)

clean:
	rm -f $(BIN)

.PHONY: all clean
