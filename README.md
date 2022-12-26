# netty

Experiments with netty in clojure

## Usage

proxy - A forwarding tcp proxy
run in repl
```
echo "GET / HTTP/1.0\r\nHOST: info.cern.ch\r\n\r\n" | nc localhost 9006
```
echo-server - As the name suggests
````    
nc localhost <port>
````


