package com.zhouzhou.node.config;

/**
 * Node configuration.
 * <p>
 * Node configuration should not change after initialization. e.g {@link NodeBuilder}.
 * </p>
 */
public class NodeConfig {

    /**
     * Minimum election timeout
     */
    private int minElectionTimeout = 3000;

    /**
     * Maximum election timeout
     */
    private int maxElectionTimeout = 4000;


    /**
     * Interval for log replication task.
     * More specifically, interval for heartbeat rpc.
     * Append entries rpc may be sent less than this interval.
     * e.g after receiving append entries result from followers.
     */
    private int logReplicationInterval = 1000;

    /**
     * Read timeout to receive response from follower.
     * If no response received from follower, resend log replication rpc.
     */
    private int logReplicationReadTimeout = 900;


}
