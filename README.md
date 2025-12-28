# 🚀 HuskyMQ Playground

这是一个用于练习和验证分布式中间件（RocketMQ, Redisson, Seata 等）核心特性的实战演练场。本项目基于 Spring Boot 3.4.1 + JDK 21 构建，重点探索消息队列的高级特性与最佳实践。

## 🛠 技术栈

- **核心框架**: Spring Boot 3.4.1
- **语言版本**: JDK 21
- **消息队列**: RocketMQ 5.x
- **缓存/分布式锁**: Redis (Redisson)
- **数据库**: MySQL
- **ORM**: MyBatis-Plus
- **分布式事务**: Seata (计划中)

## 🌟 核心演进路线 (RocketMQ)

本项目通过三个版本的消费者实现，展示了从“手动挡”到“自动挡”的架构演进过程，重点解决了**消息幂等性**和**代码复用**问题。

### V1: 手动版 (Manual)
- **类**: `AsyncSaveConsumerV1`
- **特点**: 在业务代码中显式调用 `RedissonClient` 进行查重。
- **缺点**: 业务逻辑与幂等逻辑高度耦合，代码侵入性强。

### V2: AOP 版 (Annotation)
- **类**: `AsyncSaveConsumerV2`
- **特点**: 引入自定义注解 `@RocketMQIdempotent`。
- **机制**: 利用 Spring AOP 环绕通知，在方法执行前自动检查 Redis 锁。
- **缺点**: 仍需手动处理 `MessageExt` 的反序列化。

### V3: 终极版 (Generic Base Class) - **推荐**
- **类**: `AsyncSaveConsumerV3`
- **特点**: 继承泛型基类 `BaseRocketMQListener<T>`。
- **优势**:
    1.  **自动反序列化**: 基类利用 Jackson 自动将 JSON 转为 DTO 对象。
    2.  **完美兼容 AOP**: 重写 `onMessage` 入口挂载注解，业务逻辑下沉至 `handleMessage`。
    3.  **配置统一**: 基类统一管理 `CONSUME_FROM_LAST_OFFSET` 等配置。
    4.  **灵活模式**: 支持并发消费 (`CONCURRENTLY`) 与顺序消费 (`ORDERLY`) 切换。

## 🛡️ 关键架构设计

### 1. 幂等性设计 (Idempotency)
为了防止网络抖动、Broker 重试或人工重置位点导致的消息重复消费，我们实现了一套基于 Redis 的幂等机制：
- **原理**: 使用 Redisson 的 `setIfAbsent` (SETNX) 实现分布式锁/标记。
- **Key 生成**: 优先使用业务唯一标识 (如 `TraceId`, `OrderNo`)，若无则降级使用 `MessageKey`。
- **验证**: 通过 RocketMQ 控制台 "Resend Message" 或 "Reset Offset" 验证，系统能准确拦截重复消息，日志显示 `Duplicate message detected`。

### 2. 顺序消费 (Orderly Consumption)
我们在 V3 版本中验证了 RocketMQ 的顺序消费特性：
- **生产者**: 使用 `syncSendOrderly` 并指定 Sharding Key (如 `userId`)，确保同一组消息进入同一 Queue。
- **消费者**: 配置 `consumeMode = ConsumeMode.ORDERLY`。
- **效果**: 当某条消息消费失败时，Broker 会锁定队列并阻塞后续消息，直到该消息重试成功。验证了“前序失败，后续等待”的严格顺序性。

## ⛓️ 事务消息 (Transactional Message) - 订单场景

本项目通过“创建订单”这一典型场景，演示了 RocketMQ 事务消息如何保证**上游业务（数据库操作）**与**下游通知（消息发送）**的最终一致性。

### 1. 核心流程

1.  **Producer (DemoController)**:
    -   调用 `rocketMQTemplate.sendMessageInTransaction` 发送一条 **Half Message** (半消息)。
    -   此时消息对消费者不可见。

2.  **Broker & TransactionListener**:
    -   Broker 收到半消息后，回调 `OrderTransactionListener` 的 `executeLocalTransaction` 方法。
    -   Listener 内部调用 `OrderService`，该 Service 在一个 `@Transactional` 注解的本地事务中，**同时**执行以下两件事：
        -   **a. 插入订单数据** (`purchase_order` 表)
        -   **b. 插入事务日志** (`order_transaction` 表，记录了 RocketMQ 的事务 ID `txId`)
    -   根据本地事务的执行结果，返回三种状态：
        -   `COMMIT`: 本地事务成功，通知 Broker 投递消息。
        -   `ROLLBACK`: 本地事务失败，通知 Broker 删除半消息。
        -   `UNKNOWN`: 因网络超时或应用崩溃，未返回状态。

3.  **Broker & 回查机制**:
    -   如果收到 `UNKNOWN` 或长时间未收到任何回执，Broker 会**主动回查** `OrderTransactionListener` 的 `checkLocalTransaction` 方法。
    -   该方法通过查询 `order_transaction` 表中是否存在 `txId` 来判断本地事务是否成功。
        -   **查到了**: 返回 `COMMIT`。
        -   **没查到**: 返回 `ROLLBACK`。

4.  **Consumer (OrderConsumerV3)**:
    -   只有当 Broker 最终确认消息为 `COMMIT` 状态时，才会将消息投递给消费者。
    -   消费者收到消息后，执行自己的业务逻辑（如打印订单详情）。

### 2. 接口测试

| 接口 | 方法 | 描述 | 示例 Body |
| --- | --- | --- | --- |
| `/api/demo/transactional-order` | POST | 发送事务消息 (测试最终一致性) | `{"buyer": "alice", "itemName": "Keyboard", "quantity": 1, "amount": 199.00}` |

#### 测试场景

-   **正常提交**: 使用 `buyer: "alice"`，本地事务成功，消息被正常消费。
-   **本地回滚**: 使用 `buyer: "rollback"`，本地事务直接失败，消息被删除，下游无感知。
-   **状态未知与回查**: 使用 `buyer: "unknown"`，本地事务成功但返回 `UNKNOWN`，Broker 会在稍后回查，并最终根据事务日志表决定投递消息。要令消息失效，可手动删除事务日志记录、破坏一致性，来人造一种“网络超时且本地事务未提交”的场景。

## 🧪 接口测试

项目提供了 `DemoController` 用于触发测试场景：

| 接口 | 方法 | 描述 | 示例 Body |
| --- | --- | --- | --- |
| `/api/demo/log` | POST | 发送普通消息 (测试幂等) | `{"username": "test", "operation": "login"}` |
| `/api/demo/ordered-log` | POST | 发送顺序消息 (测试顺序消费) | `{"username": "orderUser", "operation": "step"}` |
| `/api/demo/transactional-order` | POST | 发送事务消息 (测试最终一致性) | `{"buyer": "alice", "itemName": "Keyboard", "quantity": 1, "amount": 199.00}` |

## 📝 待办事项 (Todo)

- [x] RocketMQ 基础集成
- [x] 幂等性架构 (V1 -> V3)
- [x] 顺序消费验证
- [ ] 延迟消息与死信队列处理
- [x] 事务消息验证
- [ ] Seata 分布式事务集成
- [ ] 消息轨迹追踪

---
*Happy Coding!*
