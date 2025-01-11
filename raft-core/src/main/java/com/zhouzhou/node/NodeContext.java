package com.zhouzhou.node;


import com.google.common.eventbus.EventBus;
import com.zhouzhou.node.config.NodeConfig;
import com.zhouzhou.node.store.NodeStore;
import com.zhouzhou.rpc.Connector;
import com.zhouzhou.schedule.Scheduler;
import com.zhouzhou.support.TaskExecutor;

/**
 * Node context.
 * <p>
 * Node context should not change after initialization. e.g {@link NodeBuilder}.
 * </p>
 */
public class NodeContext {

    /**
     * 当前节点的ID
     */
    private NodeId selfId;
    /**
     * 成员列表信息
     */
    private NodeGroup group;
    /**
     * 日志
     */
    private Object log;
    /**
     * RPC组件
     */
    private Connector connector;
    /**
     * 节点存储
     */
    private NodeStore store;


    private EventBus eventBus;


    private Scheduler scheduler;

    private TaskExecutor taskExecutor;

    private NodeConfig config;


    public Connector connector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public NodeStore store() {
        return store;
    }

    public void setStore(NodeStore store) {
        this.store = store;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public TaskExecutor taskExecutor() {
        return taskExecutor;
    }

    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }


    public NodeId selfId() {
        return selfId;
    }

    public void setSelfId(NodeId selfId) {
        this.selfId = selfId;
    }

    public NodeGroup group() {
        return group;
    }

    public void setGroup(NodeGroup group) {
        this.group = group;
    }


    public NodeConfig config() {
        return config;
    }

    public void setConfig(NodeConfig config) {
        this.config = config;
    }





}
