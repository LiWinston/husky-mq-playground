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

### 2. 挑战：多业务场景下的事务消息

当系统需要支持多种事务消息时（例如，订单事务、购物车事务），会遇到一个架构难题：默认的 `RocketMQTemplate` 只能绑定一个 `RocketMQTransactionListener`。

#### 错误尝试与原因
最初，我们尝试通过为 `@RocketMQTransactionListener` 添加 `txProducerGroup` 属性来区分不同的监听器，但这会导致编译失败，因为该注解并不支持此属性。

#### 解决方案：`@ExtRocketMQTemplateConfiguration`

正确的做法是为每个事务场景创建一个专用的 `RocketMQTemplate` Bean。这得益于 `rocketmq-spring-boot-starter` 提供的 `@ExtRocketMQTemplateConfiguration` 注解。

> 这一解决思路受到了 [阿里云社区技术博客](https://www.alibabacloud.com/blog/597897) 的启发。

1.  **创建多个 Template 类**:
    为每个业务（如购物车、订单）创建一个继承自 `RocketMQTemplate` 的新类，并使用 `@ExtRocketMQTemplateConfiguration` 注解配置其独立的 `producer-group`。

    ```java
    // CartRocketMQTemplate.java
    @ExtRocketMQTemplateConfiguration(group = "cart-tx-group")
    public class CartRocketMQTemplate extends RocketMQTemplate {}

    // OrderRocketMQTemplate.java
    @ExtRocketMQTemplateConfiguration(group = "order-tx-group")
    public class OrderRocketMQTemplate extends RocketMQTemplate {}
    ```

    **注意**: 在实践中发现，`@ExtRocketMQTemplateConfiguration` 的 `value` (或 `name`) 属性会与 Spring 的 Bean 命名机制产生冲突，导致启动失败。因此，我们省略了 `value` 属性，让 Spring 自动生成默认的 Bean 名称（如 `cartRocketMQTemplate`）。

2.  **绑定 Listener**:
    在对应的 `RocketMQTransactionListener` 中，使用 `rocketMQTemplateBeanName` 属性精确绑定到我们刚刚创建的 Template Bean。

    ```java
    // CartTransactionListener.java
    @RocketMQTransactionListener(rocketMQTemplateBeanName = "cartRocketMQTemplate")
    public class CartTransactionListener implements RocketMQLocalTransactionListener { ... }

    // OrderTransactionListener.java
    @RocketMQTransactionListener(rocketMQTemplateBeanName = "orderRocketMQTemplate")
    public class OrderTransactionListener implements RocketMQLocalTransactionListener { ... }
    ```

3.  **在生产者中注入**:
    在业务生产者 (`ECommerceProducer`) 中，注入特定业务的 Template，而不是通用的 `RocketMQTemplate`。

    ```java
    @Service
    @RequiredArgsConstructor
    public class ECommerceProducer {
        private final CartRocketMQTemplate cartRocketMQTemplate;
        private final OrderRocketMQTemplate orderRocketMQTemplate;
        // ...
    }
    ```

通过这种方式，我们成功地为不同的业务场景隔离了事务消息的发送和监听逻辑，实现了多事务监听器的共存。

### 3. 实践案例：级联事务 (购物车 -> 订单)

我们设计了一个级联场景来展示事务消息的组合能力：用户加购物车成功后，自动触发创建订单。

-   **流程**:
    1.  **购物车环**: `/transactional-cart` 接口触发一个事务消息，在本地“加车”成功后，向 `cart-topic` 发送消息。
    2.  **订单环**: `CartConsumer` 监听到消息后，**再次**发起一个事务消息，在本地“创建订单”成功后，向 `order-topic` 发送消息。
    3.  **最终消费**: `OrderConsumer` 监听到 `order-topic` 消息，完成最终业务。

-   **核心思想**:
    通过消息队列将一个复杂的分布式事务，拆解为多个由事务消息保证原子性的、独立的本地事务环节。这体现了**事件驱动**和 **Saga 模式**的思想，实现了服务解耦和最终一致性。

### 4. 级联事务的局限性与分布式事务的引入

我们通过“购物车 -> 订单”的级联事务展示了如何通过事件驱动的方式将一个大事务拆分为多个小事务。然而，这种模式也暴露了其固有的局限性。

RocketMQ 的事务消息本质上是**生产侧事务**，它只能保证**本地事务**与**消息发送**这两个操作的原子性。在级联场景中，它能做到：
-   如果“加购物车”的本地事务失败，那么“创建订单”的消息就不会被发送。

但它**无法**做到：
-   如果“创建订单”环节由于业务规则（如库存不足、风控拦截）或系统故障（下游服务不可用且持续重试失败）而最终失败，已经成功执行的“加购物车”操作**无法自动回滚**。

这种“只管杀，不管埋”的特性，决定了它适用于那些对最终一致性要求高、但允许下游失败且无需回滚上游的业务流程。对于需要严格保证全局事务原子性的场景（要么全部成功，要么全部回滚），这便引出了我们下一个要探索的核心主题——**分布式事务**。

我们将在后续章节中，引入 Seata 框架，来解决这一挑战。


### 5. 接口测试

| 接口 | 方法 | 描述 | 示例 Body |
| --- | --- | --- | --- |
| `/api/demo/transactional-cart` | POST | 级联事务测试 (购物车 -> 订单) | `{"username": "bob", "itemName": "Mouse", "quantity": 2}` |
| `/api/demo/transactional-order` | POST | 发送事务消息 (测试最终一致性) | `{"buyer": "alice", "itemName": "Keyboard", "quantity": 1, "amount": 199.00}` |

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
