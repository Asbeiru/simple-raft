package com.zhouzhou.node;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.FutureCallback;
import com.zhouzhou.node.role.*;
import com.zhouzhou.node.store.NodeStore;
import com.zhouzhou.rpc.message.*;
import com.zhouzhou.schedule.ElectionTimeout;
import com.zhouzhou.schedule.LogReplicationTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.Objects;
import java.util.concurrent.Future;

public class NodeImpl implements Node {

    private static final Logger logger = LoggerFactory.getLogger(NodeImpl.class);


    // callback for async tasks.
    private static final FutureCallback<Object> LOGGING_FUTURE_CALLBACK = new FutureCallback<Object>() {
        @Override
        public void onSuccess(@Nullable Object result) {
        }

        @Override
        public void onFailure(@Nonnull Throwable t) {
            logger.warn("failure", t);
        }
    };

    /**
     * 组件门面给封装类
     */
    private final NodeContext context;

    /**
     * 当前的节点角色
     */
    private volatile AbstractNodeRole role;

    @GuardedBy("this")
    private boolean started;


    /**
     * Create with context.
     *
     * @param context context
     */
    NodeImpl(NodeContext context) {
        this.context = context;
    }


    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        // 注册自己到事件总线
        context.eventBus().register(this);
        // 初始化RPC组件
        context.connector().initialize();
        // 根据Raft算法，当一个节点启动时，它的初始角色是Follower。在启动的时候，需要从存储中加载term和votedFor信息，然后变成Follower角色
        // load term, votedFor from store and become follower
        NodeStore store = context.store();
        changeToRole(new FollowerNodeRole(store.getTerm(), store.getVotedFor(), null, scheduleElectionTimeout()));
        started = true;
    }

    /**
     * Change role.统一的角色变更方法
     *
     * @param newRole new role
     */
    private void changeToRole(AbstractNodeRole newRole) {
        if (!isStableBetween(role, newRole)) {
            logger.debug("node {}, role state changed -> {}", context.selfId(), newRole);
            RoleState state = newRole.getState();

            // update store
            NodeStore store = context.store();
            store.setTerm(state.getTerm());
            store.setVotedFor(state.getVotedFor());

            // notify listeners
//            roleListeners.forEach(l -> l.nodeRoleChanged(state));
        }
        role = newRole;
    }

    /**
     * Check if stable between two roles.
     * <p>
     * It is stable when role name not changed and role state except timeout/task not change.
     * </p>
     * <p>
     * If role state except timeout/task not changed, it should not update store or notify listeners.
     * </p>
     *
     * @param before role before
     * @param after  role after
     * @return true if stable, otherwise false
     * @see AbstractNodeRole#stateEquals(AbstractNodeRole)
     */
    private boolean isStableBetween(AbstractNodeRole before, AbstractNodeRole after) {
        assert after != null;
        return before != null && before.stateEquals(after);
    }

    /**
     * Schedule election timeout.
     *
     * @return election timeout
     */
    private ElectionTimeout scheduleElectionTimeout() {
        return context.scheduler().scheduleElectionTimeout(this::electionTimeout);
    }


    /**
     * Election timeout
     * <p>
     * Source: scheduler
     * </p>
     */
    void electionTimeout() {
        context.taskExecutor().submit(this::doProcessElectionTimeout, LOGGING_FUTURE_CALLBACK);
    }

    private void doProcessElectionTimeout() {
        // leader 角色不可能会有选举超时
        if (role.getName() == RoleName.LEADER) {
            logger.warn("node {}, current role is leader, ignore election timeout", context.selfId());
            return;
        }

        // follower: start election。对于follower节点来说，是需要发起选举的
        // candidate: restart election。对于candidate节点来说，是需要再次发起选举的
        // 选举term +1
        int newTerm = role.getTerm() + 1;
        role.cancelTimeoutOrTask();

//        if (context.group().isStandalone()) {
//            if (context.mode() == NodeMode.STANDBY) {
//                logger.info("starts with standby mode, skip election");
//            } else {

        // become leader
//                logger.info("become leader, term {}", newTerm);
//                resetReplicatingStates();
//                changeToRole(new LeaderNodeRole(newTerm, scheduleLogReplicationTask()));
//                context.log().appendEntry(newTerm); // no-op log
//            }
//        } else {
        //  变成 candidate 角色
        logger.info("start election");
        changeToRole(new CandidateNodeRole(newTerm, scheduleElectionTimeout()));

        //  发送 request vote 请求
        RequestVoteRpc rpc = new RequestVoteRpc();
        rpc.setTerm(newTerm);
        rpc.setCandidateId(context.selfId());
        rpc.setLastLogIndex(0);
        rpc.setLastLogTerm(0);
        NodeGroup group = context.group();
        context.connector().sendRequestVote(rpc, group.listEndpointOfMajorExceptSelf());
//        }
    }


    //-------------------- handle @Subscribe event from EventBus --------------------

    /**
     * Receive request vote rpc.
     * <p>
     * Source: connector.
     * </p>
     *
     * @param rpcMessage rpc message
     */
    @Subscribe
    public void onReceiveRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {
        context.taskExecutor().submit(
                () -> context.connector().replyRequestVote(doProcessRequestVoteRpc(rpcMessage), rpcMessage),
                LOGGING_FUTURE_CALLBACK
        );
    }

    /**
     * @param rpcMessage
     * @return
     */
    private RequestVoteResult doProcessRequestVoteRpc(RequestVoteRpcMessage rpcMessage) {

        // skip non-major node, it maybe removed node
        if (!context.group().isMemberOfMajor(rpcMessage.getSourceNodeId())) {
            logger.warn("receive request vote rpc from node {} which is not major node, ignore", rpcMessage.getSourceNodeId());
            return new RequestVoteResult(role.getTerm(), false);
        }

        // reply current term if result's term is smaller than current one
        // 如果请求的term小于当前节点的term，那么直接拒绝投票，并且返回自己当前的term
        RequestVoteRpc rpc = rpcMessage.get();
        if (rpc.getTerm() < role.getTerm()) {
            logger.debug("term from rpc < current term, don't vote ({} < {})", rpc.getTerm(), role.getTerm());
            return new RequestVoteResult(role.getTerm(), false);
        }
        // FIXME: for test，我们直接投票给对方
        boolean voteForCandidate = true;
        // step down if result's term is larger than current term
        // 如果请求的term大于当前节点的term，那么直接变成follower，并且投票给对方
        if (rpc.getTerm() > role.getTerm()) {
//            boolean voteForCandidate = !context.log().isNewerThan(rpc.getLastLogIndex(), rpc.getLastLogTerm());
            becomeFollower(rpc.getTerm(), (voteForCandidate ? rpc.getCandidateId() : null), null, true);
            return new RequestVoteResult(rpc.getTerm(), voteForCandidate);
        }
        // 本地的term和请求的term相等
        assert rpc.getTerm() == role.getTerm();
        switch (role.getName()) {
            case FOLLOWER:
                FollowerNodeRole follower = (FollowerNodeRole) role;
                NodeId votedFor = follower.getVotedFor();
                // 在以下两种情况下，我们会投票给对方。【并且在投票之后，变成follower 角色】
                // 1. 自己尚未投票给其他节点，且对方的日志比当前节点的日志新
                // 2. 自己已经投票给对方
                if ((votedFor == null && voteForCandidate) || // case 1
                        Objects.equals(votedFor, rpc.getCandidateId())) { // case 2
                    becomeFollower(role.getTerm(), rpc.getCandidateId(), null, true);
                    return new RequestVoteResult(rpc.getTerm(), true);
                }
                return new RequestVoteResult(role.getTerm(), false);
            case CANDIDATE: // 如果自己是candidate角色，那么candidate角色只会投票给自己
            case LEADER:
                return new RequestVoteResult(role.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }

    /**
     * Become follower.
     *
     * @param term                    term
     * @param votedFor                voted for
     * @param leaderId                leader id
     * @param scheduleElectionTimeout schedule election timeout or not
     */
    private void becomeFollower(int term, NodeId votedFor, NodeId leaderId, boolean scheduleElectionTimeout) {
        role.cancelTimeoutOrTask();
        if (leaderId != null && !leaderId.equals(role.getLeaderId(context.selfId()))) {
            logger.info("current leader is {}, term {}", leaderId, term);
        }
        ElectionTimeout electionTimeout = scheduleElectionTimeout ? scheduleElectionTimeout() : ElectionTimeout.NONE;
        changeToRole(new FollowerNodeRole(term, votedFor, leaderId, electionTimeout));
    }


    /**
     * 处理收到 RequestVoteResult （即 request vote rpc 的响应）后的逻辑
     *
     * @param result
     */
    @Subscribe
    public void onReceiveRequestVoteResult(RequestVoteResult result) {
        context.taskExecutor().submit(() -> doProcessRequestVoteResult(result), LOGGING_FUTURE_CALLBACK);
    }


    private void doProcessRequestVoteResult(RequestVoteResult result) {

        // 如果对方的term比自己的term大，那么退变成follower 角色
        if (result.getTerm() > role.getTerm()) {
            becomeFollower(result.getTerm(), null, null, true);
            return;
        } else if (result.getTerm() < role.getTerm()) {
            // ignore stale term
            logger.debug("receive request vote result and result term is smaller, ignore");
            return;
        }

        // 如果自己不是candidate角色，那么直接忽略
        if (role.getName() != RoleName.CANDIDATE) {
            logger.debug("receive request vote result and current role is not candidate, ignore");
            return;
        }

        // 如果对方没有投票给自己，那么直接忽略
        if (!result.isVoteGranted()) {
            return;
        }
        // 如果对方投票给自己，那么更新票数
        int currentVotesCount = ((CandidateNodeRole) role).getVotesCount() + 1;
        // 获取major节点的数量
        int countOfMajor = context.group().getCountOfMajor();
        logger.debug("votes count {}, major node count {}", currentVotesCount, countOfMajor);
        // 取消选举超时任务
        role.cancelTimeoutOrTask();
        if (currentVotesCount > countOfMajor / 2) {

            // become leader
            logger.info("become leader, term {}", role.getTerm());
//            resetReplicatingStates();
            // raft算法要求，当一个节点成为leader时，需要立刻向其他节点发送心跳包，从而重置其他节点的选举超时时间，使集群的主从关系稳定下来
            changeToRole(new LeaderNodeRole(role.getTerm(), scheduleLogReplicationTask()));
//            context.log().appendEntry(role.getTerm()); // no-op log
            context.connector().resetChannels(); // close all inbound channels
        } else {

            // 如果票数不够，那么继续保持candidate角色。并且重新设置选举超时任务
            changeToRole(new CandidateNodeRole(role.getTerm(), currentVotesCount, scheduleElectionTimeout()));
        }
    }

    /**
     * Schedule log replication task.
     *
     * @return log replication task
     */
    private LogReplicationTask scheduleLogReplicationTask() {
        return context.scheduler().scheduleLogReplicationTask(this::replicateLog);
    }


    /**
     * Replicate log.
     * <p>
     * Source: scheduler.
     * </p>
     */
    void replicateLog() {
        context.taskExecutor().submit(this::doReplicateLog, LOGGING_FUTURE_CALLBACK);
    }

    /**
     * Replicate log to other nodes.
     */
    private void doReplicateLog() {
        // just advance commit index if is unique node
//        if (context.group().isStandalone()) {
//            context.log().advanceCommitIndex(context.log().getNextIndex() - 1, role.getTerm());
//            return;
//        }
        logger.debug("replicate log");
        // 给其他节点发送日志复制请求
        for (GroupMember member : context.group().listReplicationTarget()) {
            if (member.shouldReplicate(context.config().getLogReplicationReadTimeout())) {
                doReplicateLog(member, context.config().getMaxReplicationEntries());
            } else {
                logger.debug("node {} is replicating, skip replication task", member.getId());
            }
        }
    }

    /**
     * Replicate log to specified node.
     * <p>
     * Normally it will send append entries rpc to node. And change to install snapshot rpc if entry in snapshot.
     * </p>
     *
     * @param member     node
     * @param maxEntries max entries
     */
    private void doReplicateLog(GroupMember member, int maxEntries) {
        member.replicateNow();
        // FIXME：more logic
    }

    // ----------------- 处理收到AppendEntriesRpcMessage -----------------

    /**
     * 非leader节点收到leader节点的心跳包
     * - 非leader节点收到leader节点的心跳包，会重置自己的选举超时时间
     *
     * @param rpcMessage
     */
    @Subscribe
    public void onReceiveAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
        context.taskExecutor().submit(() ->
                        context.connector().replyAppendEntries(doProcessAppendEntriesRpc(rpcMessage), rpcMessage),
                LOGGING_FUTURE_CALLBACK
        );
    }


    private AppendEntriesResult doProcessAppendEntriesRpc(AppendEntriesRpcMessage rpcMessage) {
        AppendEntriesRpc rpc = rpcMessage.get();

        // case 1：如果对方的term比自己的term小，则回复自己的term，并且不追加日志
        if (rpc.getTerm() < role.getTerm()) {
            return new AppendEntriesResult(rpc.getMessageId(), role.getTerm(), false);
        }

        // case 2：如果对方的term比自己的term大，则变成follower角色，并且追加日志
        if (rpc.getTerm() > role.getTerm()) {
            becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
            return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
        }

        assert rpc.getTerm() == role.getTerm();
        switch (role.getName()) {
            case FOLLOWER: // case 3: follower，设置leaderId，并且追加日志

                // reset election timeout and append entries
                becomeFollower(rpc.getTerm(), ((FollowerNodeRole) role).getVotedFor(), rpc.getLeaderId(), true);
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
            case CANDIDATE: // case 4: candidate，
                // 如果有两个candidate节点，并且另外一个candidate节点赢得了选举，那么当前节点会变成follower角色 并且重置选举超时时间并且追加日志

                // more than one candidate but another node won the election
                becomeFollower(rpc.getTerm(), null, rpc.getLeaderId(), true);
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), appendEntries(rpc));
            case LEADER: // case 5: leader，不可能出现这种情况
                logger.warn("receive append entries rpc from another leader {}, ignore", rpc.getLeaderId());
                return new AppendEntriesResult(rpc.getMessageId(), rpc.getTerm(), false);
            default:
                throw new IllegalStateException("unexpected node role [" + role.getName() + "]");
        }
    }

    /**
     * Append entries and advance commit index if possible.
     *
     * @param rpc rpc
     * @return {@code true} if log appended, {@code false} if previous log check failed, etc
     */
    private boolean appendEntries(AppendEntriesRpc rpc) {
        // FIXME: more logic
        return true;
    }

    // ----------------- 处理收到AppendEntriesResult -----------------


    /**
     * Receive append entries result.
     *
     * @param resultMessage result message
     */
    @Subscribe
    public void onReceiveAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        context.taskExecutor().submit(() -> doProcessAppendEntriesResult(resultMessage), LOGGING_FUTURE_CALLBACK);
    }

    Future<?> processAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        return context.taskExecutor().submit(() -> doProcessAppendEntriesResult(resultMessage));
    }

    private void doProcessAppendEntriesResult(AppendEntriesResultMessage resultMessage) {
        AppendEntriesResult result = resultMessage.get();

        // 如果对方的term比自己的term大，那么退变成follower 角色
        if (result.getTerm() > role.getTerm()) {
            becomeFollower(result.getTerm(), null, null, true);
            return;
        }

        // check role
        if (role.getName() != RoleName.LEADER) {
            logger.warn("receive append entries result from node {} but current node is not leader, ignore", resultMessage.getSourceNodeId());
            return;
        }

//        // dispatch to new node catch up task by node id
//        if (newNodeCatchUpTaskGroup.onReceiveAppendEntriesResult(resultMessage, context.log().getNextIndex())) {
//            return;
//        }
//
//        NodeId sourceNodeId = resultMessage.getSourceNodeId();
//        GroupMember member = context.group().getMember(sourceNodeId);
//        if (member == null) {
//            logger.info("unexpected append entries result from node {}, node maybe removed", sourceNodeId);
//            return;
//        }
//
//        AppendEntriesRpc rpc = resultMessage.getRpc();
//        if (result.isSuccess()) {
//            if (!member.isMajor()) {  // removing node
//                if (member.isRemoving()) {
//                    logger.debug("node {} is removing, skip", sourceNodeId);
//                } else {
//                    logger.warn("unexpected append entries result from node {}, not major and not removing", sourceNodeId);
//                }
//                member.stopReplicating();
//                return;
//            }
//
//            // peer
//            // advance commit index if major of match index changed
//            if (member.advanceReplicatingState(rpc.getLastEntryIndex())) {
//                context.log().advanceCommitIndex(context.group().getMatchIndexOfMajor(), role.getTerm());
//            }
//
//            // node caught up
//            if (member.getNextIndex() >= context.log().getNextIndex()) {
//                member.stopReplicating();
//                return;
//            }
//        } else {
//
//            // backoff next index if failed to append entries
//            if (!member.backOffNextIndex()) {
//                logger.warn("cannot back off next index more, node {}", sourceNodeId);
//                member.stopReplicating();
//                return;
//            }
//        }
//
//        // replicate log to node immediately other than wait for next log replication
//        doReplicateLog(member, context.config().getMaxReplicationEntries());
    }


    @Override
    public void stop() throws InterruptedException {
        if (!started) {
            throw new IllegalStateException("node not started");
        }
        context.scheduler().stop();
//        context.log().close();
        context.connector().close();
        context.store().close();
        context.taskExecutor().shutdown();
//        context.groupConfigChangeTaskExecutor().shutdown();
        started = false;

    }
}
