1. https://github.com/atomix/atomix-raft-storage
# 状态数据分析
## 1.1 需要持久化的状态数据
1. 每台服务器启动或者关闭的时候，必须保存的数据如下：
- 服务器的当前任期号（currentTerm）和投票给谁（votedFor）
- 日志条目（log entries），即og[]数组，注意第一个日志的索引是1，而不是0
2. 服务器在运行时，需要保存的数据如下：
   每台服务器在运行中需要保存的数据如下：
- commitIndex：已知的最大的已经被提交的日志条目的索引值
- lastApplied：已经被应用到状态机的最大的日志条目的索引值
- matchIndex[]：对于每一台服务器，已知的已经复制到该服务器的最大的日志条目的索引值
  **对于上面的Leader服务器可变状态，非leader服务器有以下可变状态数据：**
- follower角色服务器的leaderId：当前leader的id
- candidate角色服务器的voteCount：获得的选票数
  **另外不管什么角色的服务器，都需要保存当前服务器的角色Role**
3. leader 服务器的可变状态数据
- nextIndex[]：对于每一台服务器，发送到该服务器的下一个日志条目的索引值
- matchIndex[]：对于每一台服务器，已知的已经复制到该服务器的最大的日志条目的索引值
# 服务器ID的设计
1. {@see com.zhouzhou.node.NodeId}
# 静态数据分析
1. 配置
- 最小选举超时时间间隔：{@see com.zhouzhou.node.Node#MIN_ELECTION_TIMEOUT}
- 最大选举超时时间间隔：{@see com.zhouzhou.node.Node#MAX_ELECTION_TIMEOUT}
- 日志复制时间间隔：{@see com.zhouzhou.node.Node#REPLICATION_INTERVAL}
2. 集群成员列表
   如果成员列表可以改变，就需要考虑在什么地方存储最新的成员列表，有如下三种方式：
- 和状态数据一样持久化
- 在日志中存放差分数据，即增减成员的日志
- 在日志中存储变更前的列表和差分数据
  **方案2的示例如下**：
- 集群成员列表：A,B,C
- add node D
- remove node B
- 集群成员列表：A,C,D
  **方案3的示例如下**
- 集群成员列表：A,B,C
- 变更前：A,B,C ，差分数据：add D
- 变更前：A,B,C ，差分数据：remove B
- 集群成员列表：A,C,D
# 集群成员信息编码
1. 集群成员表
   {@see com.zhouzhou.node.NodeGroup}
   **主要包含以下属性和方法：**
- 集群成员列表
2. 集群成员信息
   {@see com.zhouzhou.node.GroupMember}，其中
- NodeEndpoint 节点 IP 和端口
- NodeId 节点ID
  **ReplicatingState**
  {@see com.zhouzhou.node.ReplicatingState} 表示一个节点的复制状态，其中
- nextIndex: 下一个要发送的日志条目的索引
- matchIndex: 已经复制到该节点的最大的日志条目的索引
- replicating: 是否正在复制
- lastReplicatedAt: 最后一次复制的时间