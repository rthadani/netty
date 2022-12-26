(ns server.echo
  (:import [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.channel ChannelOption ChannelInboundHandlerAdapter 
            ChannelFutureListener ChannelInitializer ChannelHandler]
           [io.netty.channel.socket.nio NioServerSocketChannel]
           [io.netty.handler.logging LoggingHandler LogLevel]))


(defn echo-handler
  []
  (proxy [ChannelInboundHandlerAdapter] []
    (exceptionCaught [ctx cause])
    (channelRead [ctx msg]
      (.write ctx msg)
      (.flush ctx))))

(defn init-server-bootstrap
  [group handlers]
  (.. (ServerBootstrap.)
      (group group) ;we can keep two thread pools one for the bossgroup (the bound socket) and one for the child channels if we need that
      (channel NioServerSocketChannel)
      (childHandler
        (proxy [ChannelInitializer] []
          (initChannel [channel]
            (.. channel
                  (pipeline)
                  (addLast (into-array ChannelHandler handlers))))))
      (childOption ChannelOption/SO_KEEPALIVE true)))

(defn server
  [port handlers]
  (let [event-loop-group (NioEventLoopGroup.)
        bootstrap (init-server-bootstrap event-loop-group handlers)
        channel (.. bootstrap (bind port) sync channel)]
    (-> channel
        .closeFuture
        (.addListener
          (proxy [ChannelFutureListener] []
            (operationComplete [fut]
              (.shutdownGracefully event-loop-group)))))
    channel))

#_(def the-server
  (server 11111 [(LoggingHandler. "proxy" LogLevel/INFO)
                 (echo-handler)]))
#_(.close the-server)
