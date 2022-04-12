# Pillar介绍

## 简介

​	pillar是基于redis的分布式主从任务分配通用框架, 主要解决master/slave之间任务分配, 心跳机制, 宕机处理, 异步执行的通用框架, 支持高可用, 高可靠。

​	由于是pillar是基于redis, 故master/slave无需知道对方的ip端口, 只需通过redis队列进行任务发放和消费即可, 开发人员只需着重于业务开发, 无需考虑master/slave节点宕机后的处理, 也无需考虑分布式锁等问题。

​	此框架目标是希望所有想实现master/slave任务分配架构的项目无需再造轮子, 只需要基于pillar之上开发即可。

## 应用场景	

##### 1、我们先来看一个没有使用pillar的场景：

​	现在大数据组需要自研任务调度服务, 此服务的设计是master/slave架构, master负责接收后端请求的任务&访问mysql数据库&业务元数据更改等操作, 而slave节点只负责执行任务即可, slave节点可以支持的任务:spark-etl/flink-etl/hive-sql查询等。

​	架构图如下：master/slave分别部署在不同的物理机, master支持多活

![image-20220411104408597](https://tva1.sinaimg.cn/large/e6c9d24egy1h15l4zuonvj20gv0fit9j.jpg)

​	

基于上图的设计, 设计人员需要着眼解决以下几个问题:
1、执行节点可执行的任务数量应该是有限的, 例如slave节点可执行10个任务数，当此节点第11个任务到来时如何处理
2、如果master/slave节点宕机, 则正在执行的任务如何处理
3、master/slave之间如果使用http进行通信, 当slave执行一个spark任务要运行很久, 就需要解决长连接的问题
4、如何设置任务优先级

而使用pillar可以很好的解决以上问题, 后面将会详细讲解pillar的架构设计;



##### 2、另一种没有使用pillar的场景：

​	在大数据领域有很多自研的项目, 经常会遇见一种场景: 就是在服务内部将业务数据存储至redis队列中，再由自己进行消费处理; 

​	如下图：

![image-20220411105448541](https://tva1.sinaimg.cn/large/e6c9d24egy1h15lg3vwrij20ep0c6t96.jpg)



此模式下开发设计人员需要着眼解决以下几个问题:
	1、当此服务宕机后, 则正在执行的任务如何处理
	2、当此服务是分布式的, 部署多个此服务, 此时消费同一队列, 多个服务可能抢占同一个任务并执行, 需要考虑使用分布式锁

pillar同样可以解决以上问题, 后面将会详细讲解pillar的架构设计;



## 架构设计

### 架构一览

![image-20220411151336151](https://tva1.sinaimg.cn/large/e6c9d24egy1h15sxds710j216d0l8af7.jpg)



### 队列介绍

​	对于pillar而言, 所有任务都可以用字符串进行表达，开发人员可以根据任务自行设计字符串格式, 例如json

##### 1、任务队列：

​	任务队列用于slave获取任务；

​	目前的任务队列支持三种: 高优、中优、低优，开发人员可以根据业务类型进行优先级分配，slave节点会从高到低获取任务

​	redis的任务队列为sortSet，故天然支持任务优先级，开发人员可以通过参数设置或更改任务的顺序

​	**注意：由于redis有序队列是不允许重复的成员！故建议在业务层面上给任务字符串上增加唯一id, 否则相同任务字符串会被覆盖，导致下游slave只会执行一次任务**

##### 2、结果队列：

​	结果队列为redis-list结构并且只有一个结果队列，用来存放salve执行任务后的任务结果, 供master获取。

##### 3、执行队列：

​	执行队列用来存放master/slave正在执行的任务，结构为redis-hash

​	hashKey结构为: master/slave:pid@hostname

​	hashValue结构为: 任务字符串^pillar^任务字符串

​	此设计的目的是通过pid@hostname来区分各个节点, 考虑到使用pillar的服务不见得是微服务, 可能不存在port，故此处使用pid来表示唯一进程

​	hashValue通过内置字符^pillar^进行拼凑

##### 4、心跳队列：

​	master/slave节点启动时便会不断向心跳队列发送心跳，结构为redis-hash

​	hashKey结构为: master/slave:pid@hostname

​	hashValue结构为: 时间戳

​	细心的小伙伴可以发现心跳队列中的hashKey和任务执行队列中的hashKey保持一致，这样做的目的是当leader发现节点宕机后可以直接根据hashKey找到对应任务执行队列中的值, 从而获取到此节点未完成的任务。



### 职责介绍	

##### 1、master职责

​	master并不负责slave节点的宕机处理[节点健康监控由leader负责]，master主要负责任务的发放到任务队列，并且循环获取结果队列中的数据；	

​	**master心跳发送：**

​	master节点在启动时会生成自身的hashKey: master:pid@hostname 注册到心跳队列中，并每隔[默认30s]发送一次时间戳

​	**master任务发送：**

​	多个master节点发送至任务队列时无需考虑使用分布式锁

​    **master结果获取：**

​	master获取任务时内置分布式锁, 故开发人员无需考虑一个结果数据被多次消费的问题

​	master节点会先读取结果队列最左侧的数据, 然后将此数据存储至执行队列中，最后删除结果队列中数据后返回给上层开发人员。

​	**master结果提交：**

​	master处理完结果数据后, 上层开发人员需要调用一次commit接口，参数为结果数据，master会通过结果数据将执行队列中的任务数据删除，至此一套结果数据消费流程结束。



##### 2、slave职责

​	slave节点主要负责任务数据的获取和结果数据放入

​	slave的消费速度由外界开发人员决定, slave对外开放consume接口，此接口调用一次便会消费一次，若不调用则永不消费，故开发人员可以在外部设置线程池和并发数来调用slave的consume接口, 自行控制消费速度。

​	**slave心跳:**

​	slave节点在启动时会生成自身的hashKey: slave:pid@hostname 注册到心跳队列中，并每隔[默认30s]发送一次时间戳

​	**slave消费：**

​	slave消费时内置分布式锁, 故开发人员无需考虑一个结果数据被多次消费的问题

​	slave会从高到低获取任务队列中的数据

​	slave会先读取队列中权重最高的任务后放入到执行队列中，最后删除任务队列中的数据后返回给上层开发人员

​	**slave结果提交：**

​	slave在执行完任务后发送结果数据时, 上层开发人员需要调用一次commit接口，参数为结果数据和任务数据，slave会先将结果数据存储至结果队列的最右侧，然后通过任务数据去删除执行队列中的数据，至此一套任务数据消费流程结束。



##### 3、leader职责

​	PillarMaster / PillarSlave 都会定时发送心跳[默认30s]到心跳队列中, 由Leader进行监听节点存活情况。

​	Leader节点是通过抢占redis-key实现, 先抢占到key的为leader节点，其他节点会循环抢占，如果leader宕机，则其他节点晋升为leader。

​	leader节点可以是master节点，也可以是slave节点，leader节点同样要具备master/slave节点的功能，只不过增加了额外的工作量，即监听心跳队列和节点宕机后的逻辑处理。

​	**leader监控机制：**

​	leader节点默认30秒获取一次心跳队列中的数据，如果发现某个节点的hashValue和当前时间戳时间差距超过5 * 30s [默认5次]，则代表此节点宕机了，开始执行宕机处理逻辑

​	**leader执行宕机处理逻辑：**

​	先到执行队列中，将相同hashKey存储的hashValue任务列表获取到，然后根据hashKey判断宕机的节点是slave还是master节点；

​	若是slave节点, 则将所有未完成的任务放入到高优队列最右侧

​	若是master节点，则将所有未完成的任务放入到结果任务队列的最左侧

​	以上操作完成后删除执行队列中的hashKey，并且删除心跳队列里的hashKey。



## 快速使用

#### 1、pom依赖

​	pillar的pom依赖有三种：

​	1.1、通过git下载源码并install

​	1.2、下载jar包, 自行添加到本地仓库

#### springboot使用

​	

#### java程序使用



