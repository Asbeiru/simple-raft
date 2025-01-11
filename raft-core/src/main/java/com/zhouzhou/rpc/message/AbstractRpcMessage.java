package com.zhouzhou.rpc.message;


import com.zhouzhou.node.NodeId;
import com.zhouzhou.rpc.Channel;

public abstract class AbstractRpcMessage<T> {

    private final T rpc;
    private final NodeId sourceNodeId;
    private final Channel channel;

    AbstractRpcMessage(T rpc, NodeId sourceNodeId, Channel channel) {
        this.rpc = rpc;
        this.sourceNodeId = sourceNodeId;
        this.channel = channel;
    }

    public T get() {
        return this.rpc;
    }

    public NodeId getSourceNodeId() {
        return sourceNodeId;
    }

    public Channel getChannel() {
        return channel;
    }

}
