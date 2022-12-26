(ns server.proxy
  (:import [io.netty.bootstrap ServerBootstrap Bootstrap]
           [io.netty.channel ChannelOption ChannelInitializer ChannelFutureListener
            ChannelHandler ChannelInboundHandlerAdapter]
           [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.channel.socket.nio NioServerSocketChannel]
           [io.netty.handler.logging LoggingHandler LogLevel]
           [io.netty.buffer Unpooled]))

(defn init-server-bootstrap
  [group handlers-factory]
  (.. (ServerBootstrap.)
      (group group)
      (channel NioServerSocketChannel)
      (childHandler
        (proxy [ChannelInitializer] []
          (initChannel [channel]
            (let [handlers (handlers-factory)]
              (.. channel
                  (pipeline)
                  (addLast (into-array ChannelHandler handlers)))))))
      (childOption ChannelOption/SO_KEEPALIVE true)
      (childOption ChannelOption/AUTO_READ false)
      (childOption ChannelOption/AUTO_CLOSE false)))

(defn start-server [handlers-factory]
  (let [event-loop-group (NioEventLoopGroup.)
        bootstrap (init-server-bootstrap event-loop-group handlers-factory)
        channel (.. bootstrap (bind 9006) sync channel)]
    (-> channel
        .closeFuture
        (.addListener
          (proxy [ChannelFutureListener] []
            (operationComplete [fut]
              (.shutdownGracefully event-loop-group)))))
    channel))

(defn flush-and-close [channel]
  (->
    (.writeAndFlush channel Unpooled/EMPTY_BUFFER)
    (.addListener ChannelFutureListener/CLOSE)))

(defn client-proxy-handler
  [source-channel]
  (proxy [ChannelInboundHandlerAdapter] []
    (channelActive [ctx]
      (.. ctx channel read))
    (channelInactive [ctx]
      (flush-and-close source-channel))
    (channelRead [ctx msg]
      (->
        (.writeAndFlush source-channel msg)
        (.addListener
          (proxy [ChannelFutureListener] []
            (operationComplete [complete-future]
              (if (.isSuccess complete-future)
                (.. ctx channel read)
                (flush-and-close (.. ctx channel))))))))))

(defn connect-client
  [source-channel target-host target-port]
  (.. (Bootstrap.)
      (group (.eventLoop source-channel))
      (channel (.getClass source-channel))
      (option ChannelOption/SO_KEEPALIVE true)
      (option ChannelOption/AUTO_READ false)
      (option ChannelOption/AUTO_CLOSE false)
      (handler
        (proxy [ChannelInitializer] []
          (initChannel [channel]
            (.. channel
                pipeline
                (addLast (into-array ChannelHandler
                                     [(client-proxy-handler source-channel)])))
            )))
      (connect target-host target-port)))

(defn proxy-handler
  [target-host target-port]
  (let [outgoing-channel (atom nil)]
    (proxy [ChannelInboundHandlerAdapter] []
      (channelActive [ctx]
        (->
          (connect-client (.channel ctx) target-host target-port)
          (.addListener
            (proxy [ChannelFutureListener] []
              (operationComplete [complete-future]
                (if (.isSuccess complete-future)
                  (do
                    (reset! outgoing-channel (.channel complete-future))
                    (.. ctx channel read))
                  (.close (.. ctx channel))))))))
      (channelRead [ctx msg]
        (->
          (.writeAndFlush @outgoing-channel msg)
          (.addListener
            (proxy [ChannelFutureListener] []
              (operationComplete [complete-future]
                (if (.isSuccess complete-future)
                  (.. ctx channel read)
                  (flush-and-close (.. ctx channel))))))))
      (channelInactive [ctx]
        (when @outgoing-channel
          (flush-and-close @outgoing-channel))))))



#_(def open-channel (start-server (fn []
                [(LoggingHandler. "proxy" LogLevel/INFO)
                 (proxy-handler "info.cern.ch" 80 )])))

#_(.close open-channel)


