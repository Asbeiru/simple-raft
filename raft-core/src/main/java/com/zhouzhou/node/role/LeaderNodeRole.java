package com.zhouzhou.node.role;


import com.zhouzhou.node.NodeId;
import com.zhouzhou.schedule.LogReplicationTask;

import javax.annotation.concurrent.Immutable;

@Immutable
public class LeaderNodeRole extends AbstractNodeRole {

    private final LogReplicationTask logReplicationTask;

    public LeaderNodeRole(int term, LogReplicationTask logReplicationTask) {
        super(RoleName.LEADER, term);
        this.logReplicationTask = logReplicationTask;
    }

    @Override
    public NodeId getLeaderId(NodeId selfId) {
        return selfId;
    }

    @Override
    public void cancelTimeoutOrTask() {
        logReplicationTask.cancel();
    }

    @Override
    public RoleState getState() {
        return new DefaultRoleState(RoleName.LEADER, term);
    }

    @Override
    protected boolean doStateEquals(AbstractNodeRole role) {
        return true;
    }

    @Override
    public String toString() {
        return "LeaderNodeRole{term=" + term + ", logReplicationTask=" + logReplicationTask + '}';
    }
}
