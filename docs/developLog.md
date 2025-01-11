# Leader 选举实现
## 1. Raft 角色建模
1. 抽象角色类：抽象的节点角色类{@link com.zhouzhou.node.role.AbstractNodeRole}，其中有一个重要方法 {@link com.zhouzhou.node.role.AbstractNodeRole.cancelTimeoutOrTask}，用于取消每个角色的选举超时或者日志复制定时任务（每个角色有且仅有一个定时任务）
，当从一个角色转换成另外一个角色的时候，需要取消之前的定时任务，然后重新设置新的定时任务。
2. Follower 角色类：{@link com.zhouzhou.node.role.FollowerNodeRole}，Follower节点是最基本的节点角色，它是一个被动的节点角色，只能接收来自Leader节点的心跳请求，或者来自Candidate节点的投票请求。Follower节点有一个选举超时器，当超时时间到了之后，会转换成Candidate节点，然后开始发起选举。
对于Follower节点类来讲，基本的它需要维护以下状态数据
- 投过票的节点ID，用于记录当前节点投过票的节点ID，当收到其他节点的投票请求的时候，需要判断是否已经投过票了。在raft中，Follower节点只能投一次票。
- 当前节点的Leader节点ID，用于记录当前节点的Leader节点ID，当收到其他节点的心跳请求的时候，需要判断是否是当前节点的Leader节点
- 选举超时器
3. Candidate 角色类：{@link com.zhouzhou.node.role.CandidateNodeRole}，Candidate节点是一个主动的节点角色，它会发起选举，然后等待其他节点的投票。Candidate节点有一个选举超时器，当超时时间到了之后，会重新发起选举。
对于Candidate节点类来讲，基本的它需要维护以下状态数据:
- 票数，用于记录当前节点得到的票数，当票数大于半数的时候，会转换成Leader节点
- 选举超时器
另外它还有两个构造函数，一个是默认构造函数，一个是带有参数的构造函数。其中
- 默认构造函数用于节点变成Candidate节点的时候，初始化票数为1，然后发起选举
- 带有参数的构造函数用于节点收到其他节点的投票请求的时候，增加票数
4. Leader 角色类：{@link com.zhouzhou.node.role.LeaderNodeRole}，Leader节点是一个主动的节点角色，它会发送心跳请求给其他节点，然后等待其他节点的响应。Leader节点有一个心跳定时器（或者说是日志复制定时器，{@com.zhouzhou.schedule.LogReplicationTask}）

## 2. Raft 定时器组件
在Raft中主要有两种定时器，一种是选举超时器，一种是心跳定时器（或者说是日志复制定时器）。选举超时器用于Follower节点和Candidate节点，心跳定时器用于Leader节点。

**1. 对于选举超时来讲，主要有下面三种操作**：

a. 新建选举超时器
b. 重置选举超时器：主要是出现在Follower角色收到Leader节点的心跳请求的时候，需要重置选举超时器
c. 取消选举超时器

**2. 具体代码实现：**

  a. 定时器组件接口，用于管理所有的定时任务，包括选举超时任务和日志复制定时任务：{@link com.zhouzhou.schedule.Scheduler}

  b. 默认的定时器组件实现类，用于管理所有的定时任务，包括选举超时任务和日志复制定时任务：{@link com.zhouzhou.schedule.DefaultScheduler}。在 `DefaultScheduler` 种主要包含了
  四个配置参数，即：

    - 最小选举超时时间
    - 最大选举超时时间
    - 初次日志复制延迟时间{@link com.zhouzhou.schedule.DefaultScheduler.logReplicationDelay}
    - 日志复制间隔时间{@link com.zhouzhou.schedule.DefaultScheduler.logReplicationInterval}

在 `DefaultScheduler` 中主要包含了两个方法，一个是 `scheduleLogReplicationTask` 用于调度日志复制定时任务，另一个是 `scheduleElectionTimeout` 用于调度选举超时任务。
## 3. 消息建模
在 Raft 算法中主要有两种消息，一种是投票请求消息（RequestVote RPC），一种是心跳请求消息（AppendEntries RPC）。请求加上响应，一共有四种消息类。
1. 投票请求消息类：{@link com.zhouzhou.message.RequestVoteRpc}
**主要包含以下的属性：**
- 选举 Term
- 候选者节点ID，一般都是发送者自己的节点ID
- 候选者最后一条日志的索引
- 候选者最后一条日志的 Term
```java
private int term;
private NodeId candidateId;
private int lastLogIndex = 0;
private int lastLogTerm = 0;
```
2. 投票响应消息类：{@link com.zhouzhou.message.RequestVoteResult}
**主要包含以下的属性：**
- 选举 Term
- 是否投票
3. 心跳请求消息类：{@link com.zhouzhou.message.AppendEntriesRpc}
**主要包含以下的属性：**
- 领导者 Term
- 领导者节点ID，一般都是发送者自己的节点ID
- 前一条日志的索引
- 前一条日志的 Term
- 复制的日志条目
- leader的commitIndex
```java

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
```
4. 心跳响应消息类：{@link com.zhouzhou.message.AppendEntriesResult}
# 4.核心组件聚合类
**这个核心聚合组件需要整合各个组件并且负责处理组件之间的交互，核心组件需要聚合和关联的组件有如下这些**
1. AbstractNodeRole:当前节点的角色信息
2. 定时器组件：用于管理所有的定时任务，包括选举超时任务和日志复制定时任务
3. 成员列表组件：用于管理当前节点的所有的节点信息
4. 日志条目组件：用于管理当前节点的所有的日志条目信息
5. 消息发送组件（RPC组件）：用于发送消息给其他节点
在我们的设计中，我们将这些核心组件的聚合放在一个独立的门面类中，这个门面类我们称为{@link com.zhouzhou.node.NodeContext}，然后核心组件再去访问这个门面类，这样就可以实现各个组件之间的解耦。
在门面类中，我们主要包含了以下的核心属性：
- 当前节点的ID
- 成员列表信息
- 日志
- RPC组件
- 部分角色状态数据存储
- 定时器组件
```java

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
    private Log log;
    /**
     * RPC组件
     */
    private Connector connector;
    /**
     * 部分角色状态数据存储
     */
    private NodeStore store;

    /**
     * 定时器组件
     */
    private Scheduler scheduler;
```
## 4.1 RPC组件{@link com.zhouzhou.rpc.Connector}
在RPC组件中主要包含了两个方法，一个是发送消息，一个是接收消息。发送消息的方法主要是用于发送消息给其他节点，接收消息的方法主要是用于接收其他节点发送过来的消息。
其中：
1. 以send开头的方法主要是用于发送消息给其他节点。AppendEntries消息必须根据每个节点的复制进度单独设置，因此AppendEntries消息的发送方法只包含单个节点的发送方法，而不是广播发送。
2. 以reply开头的方法主要是用于回复其他节点发送过来的消息
## 4.2 任务执行接口{@link com.zhouzhou.task.TaskExecutor}
任务执行器，使用方法是context.getTaskExecutor().submit(task)。任务执行器主要用于执行一些异步任务，比如说发送消息，接收消息等。
## 4.3 状态持久化接口{@link com.zhouzhou.store.NodeStore}
在Raft中，每个节点都需要持久化一些状态数据，主要包括以下几个方面：
- voteFor：投票给谁，这是因为在Raft中，每个节点只能投一次票，所以需要记录投票给谁了。如果节点A投票给了节点B之后重启了，收到其他节点的投票请求的时候，需要判断是否已经投过票了。
- currentTerm：当前的任期号，节点A再次重启后恢复之前的voteFor状态，同时对应的currentTerm也需要恢复。
在我们的设计中，我们实现了两个存储接口，一个是内存存储{@link com.zhouzhou.store.MemoryNodeStore}，一个是文件存储{@link com.zhouzhou.store.FileNodeStore}。
## 5. 一致性核心组件 {@link com.zhouzhou.node.Node} 的实现
### 5.1 选举超时发送 RequestVoteRpc 消息给其他节点
{@com.zhouzhou.node.NodeImpl.electionTimeout}，核心是将选举term加1，然后发送RequestVoteRpc消息给其他节点。
### 5.2 接收 RequestVoteRpc 消息
{@com.zhouzhou.node.NodeImpl.onReceiveRequestVoteRpc}，核心是判断当前节点的任期号和消息中的任期号的大小，然后根据不同的情况进行处理。
1. 如果消息中的任期号小于当前节点的任期号，那么直接拒绝投票，并且返回自己当前的任期号
2. 如果请求的term大于当前节点的term，那么直接变成follower，并且投票给对方
3. 本地的term和请求的term相等，如果当前是Follower角色，在以下两种情况下，我们会投票给对方。【并且在投票之后，变成follower 角色】：
   - 自己尚未投票给其他节点，且对方的日志比当前节点的日志新
   - 自己已经投票给对方
4. 如果自己是candidate角色，那么candidate角色只会投票给自己  
### 5.3 接收 RequestVoteResult 消息
{@com.zhouzhou.node.NodeImpl.onReceiveRequestVoteResult}，核心是判断当前节点的任期号和消息中的任期号的大小，然后根据不同的情况进行处理。
1. 如果对方的term比自己的term大，那么退变成follower 角色
2. 如果自己不是candidate角色，那么直接忽略
3. 如果对方没有投票给自己，那么直接忽略
4. 如果对方投票给自己，那么增加票数，如果票数大于半数，那么变成leader角色
5. raft算法要求，当一个节点成为leader时，需要立刻向其他节点发送心跳包，从而重置其他节点的选举超时时间，使集群的主从关系稳定下来
6. 如果票数不够，那么继续保持candidate角色。并且重新设置选举超时任务
### 5.4 成为leader时，需要立刻向其他节点发送心跳包信息
Raft 算法要求，当一个节点成为leader时，需要立刻向其他节点发送心跳包，从而重置其他节点的选举超时时间，使集群的主从关系稳定下来。
{@com.zhouzhou.node.NodeImpl.replicateLog}，核心是向其他节点发送心跳包信息
### 5.5 非leader节点收到leader节点的 AppendEntriesRpc 消息
{@com.zhouzhou.node.NodeImpl.onReceiveAppendEntriesRpc}，非leader节点收到leader节点的心跳包
有如下的case情况：
1. case 1：如果对方的term比自己的term小，则回复自己的term，并且不追加日志
2. case 2：如果对方的term比自己的term大，则变成follower角色，并且追加日志
3. case 3: 如果自己是follower角色，设置leaderId，并且追加日志
4. case 4: 如果自己是candidate角色，说明有两个candidate节点，并且另外一个candidate节点赢得了选举，那么当前节点会变成follower角色 并且重置选举超时时间并且追加日志
5. case 5: 如果自己是leader角色，说明有两个leader节点，理论上不可能出现这种情况。
### 5.6 leader节点收到follower节点的 AppendEntriesResult 消息
{@com.zhouzhou.node.NodeImpl.onReceiveAppendEntriesResult}，核心是判断当前节点的任期号和消息中的任期号的大小，然后根据不同的情况进行处理。
1. 如果对方的term比自己的term大，那么退变成follower 角色
2. check role
3. TODO：add more logic

