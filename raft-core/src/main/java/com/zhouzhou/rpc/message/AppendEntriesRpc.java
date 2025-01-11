package com.zhouzhou.rpc.message;


import com.zhouzhou.node.NodeId;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class AppendEntriesRpc implements Serializable {

    /**
     * 消息id
     */
    private String messageId;
    /*

     */
    private int term;
    /**
     * 领导者id
     */
    private NodeId leaderId;

    /**
     * 前一条日志的索引
     */
    private int prevLogIndex = 0;

    /**
     * 前一条日志的任期
     */
    private int prevLogTerm;

    /**
     * 复制的日志条目
     */
    private List<Object> entries = Collections.emptyList(); // FIXME：应当使用Entry，但是暂时没有实现Entry

    /**
     * 领导者已提交的日志索引
     */
    private int leaderCommit;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public NodeId getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(NodeId leaderId) {
        this.leaderId = leaderId;
    }

    public int getPrevLogIndex() {
        return prevLogIndex;
    }

    public void setPrevLogIndex(int prevLogIndex) {
        this.prevLogIndex = prevLogIndex;
    }

    public int getPrevLogTerm() {
        return prevLogTerm;
    }

    public void setPrevLogTerm(int prevLogTerm) {
        this.prevLogTerm = prevLogTerm;
    }

    public List<Object> getEntries() {
        return entries;
    }

    public void setEntries(List<Object> entries) {
        this.entries = entries;
    }

    public int getLeaderCommit() {
        return leaderCommit;
    }

    public void setLeaderCommit(int leaderCommit) {
        this.leaderCommit = leaderCommit;
    }

    public int getLastEntryIndex() {
//        return this.entries.isEmpty() ? this.prevLogIndex : this.entries.get(this.entries.size() - 1).getIndex();
        return 0;
    }

    @Override
    public String toString() {
        return "AppendEntriesRpc{" +
                "messageId='" + messageId +
                "', entries.size=" + entries.size() +
                ", leaderCommit=" + leaderCommit +
                ", leaderId=" + leaderId +
                ", prevLogIndex=" + prevLogIndex +
                ", prevLogTerm=" + prevLogTerm +
                ", term=" + term +
                '}';
    }
}
