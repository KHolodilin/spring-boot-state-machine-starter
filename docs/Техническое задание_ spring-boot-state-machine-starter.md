# Техническое задание: `spring-boot-state-machine-starter`

**Версия документа:** 0.2  
**Статус:** Draft  
**Целевой репозиторий:** `spring-boot-state-machine-starter`  
**Стек:** Java 21+, Spring Boot 4.1, Maven, PostgreSQL

Изменение 0.1 → 0.2: зафиксированы четыре решения public API v1 (см. §3.1). Ломать их без major-версии нельзя.

## Связанные проекты

- `spring-boot-outbox-starter` — опциональная гарантированная доставка команд.
- `spring-boot-idempotency-starter` — идемпотентность бизнес/API-операций; не заменяет идемпотентность событий state machine.
- `saga-orchestrator-example` — будущий reference project, демонстрирующий Saga orchestration поверх state machine + outbox + Kafka.

---

# 1. Цель

Разработать lightweight Spring Boot starter для построения persistent state machines и business workflows.

Starter должен обеспечивать:

1. Хранение durable состояния экземпляров state machine в PostgreSQL.
2. Использование RAM как hot working set для активных machine instances.
3. Восстановление instance из PostgreSQL при cache miss или restart приложения.
4. Типизированные входящие события.
5. Единый API для synchronous и asynchronous обработки.
6. Идемпотентность входящих событий по `eventId`.
7. Optimistic locking при конкурентной обработке.
8. Переходы вида:

```text
CURRENT STATE + EVENT + optional GUARD
                  |
                  v
              NEW STATE
                  +
             0..N COMMANDS
```

9. Возможность хранить небольшой workflow context между transitions.
10. Несколько state machine definitions в одном Spring Boot приложении.
11. Опциональную интеграцию с `spring-boot-outbox-starter`.
12. Metrics, tracing, structured logging и Actuator health.
13. Schema management в режимах `create`, `validate`, `none`.

## 1.1. Не цели

Starter не должен становиться полноценным BPM/workflow engine.

В v1 не требуется:

- BPMN;
- hierarchical states;
- parallel regions;
- встроенный fork/join;
- dynamic definitions из БД;
- UI;
- scripting;
- Kafka как обязательная dependency;
- REST как обязательная dependency;
- собственная реализация Transactional Outbox;
- хранение бизнес-сущностей;
- distributed transaction coordinator.

---

# 2. Основная концепция

State machine отвечает на вопросы:

```text
Где процесс сейчас?
Что произошло?
Разрешён ли переход?
Куда перейти?
Что необходимо сделать дальше?
```

Базовая модель:

```text
                   EVENT
                     |
                     v
             StateMachineService
                     |
                     v
             TransitionEngine
                     |
          +----------+----------+
          |                     |
      current state            event
          |                     |
          +----------+----------+
                     |
                     v
                   Guard
                     |
                     v
                New State
                     |
             +-------+-------+
             |               |
          Context        Commands 0..N
```

---

# 3. Основные понятия

| Понятие | Назначение |
|---|---|
| `StateMachineDefinition` | Статическое описание states/events/transitions |
| `StateMachineInstance` | Конкретный экземпляр state machine |
| `State` | Текущее состояние процесса |
| `Event` | Факт, произошедший с процессом |
| `EventPayload` | Данные конкретного события |
| `Guard` | Условие разрешения transition |
| `Command` | Намерение выполнить следующий шаг |
| `Context` | Workflow-specific данные между transitions |
| `machineType` | Идентификатор definition |
| `machineId` | Идентификатор конкретного instance |
| `eventId` | Уникальный идентификатор конкретного события |
| `Transition` | Переход `from + event -> to` |
| `CommandPublisher` | Инфраструктурный механизм публикации commands |
| `StateMachineContext` | Типизированная обёртка workflow context |
| `TransitionResult` | Исход обработки события: success / duplicate / rejected |

---

# 3.1. Зафиксированные решения API v1

Эти четыре решения считаются частью контракта v1. Менять их — breaking change.

## D1. Typed context и явный `updateContext`

Persistence хранит context как JSONB / `Map<String, Object>`.

Public API engine, guards, command factories и context updater работают с `StateMachineContext`, а не с сырым `Map`.

```java
public interface StateMachineContext {

    Optional<String> getString(String key);

    Optional<Integer> getInt(String key);

    Optional<Long> getLong(String key);

    Optional<Boolean> getBoolean(String key);

    <T> Optional<T> get(String key, Class<T> type);

    StateMachineContext put(String key, Object value);

    StateMachineContext remove(String key);

    Map<String, Object> asMap();
}
```

`asMap()` нужен только для persistence. Мутации context — только через `put` / `remove`.

Jackson после JSONB часто отдаёт числа как `Integer` вместо `Long`. IDs в context рекомендуется хранить строками. `getInt` / `getLong` обязаны принимать любой `Number`.

Переход обновляет context только если в definition задан updater:

```java
@FunctionalInterface
public interface ContextUpdater<E, P> {

    StateMachineContext update(
            StateMachineContext context,
            StateMachineEvent<E, P> event);
}
```

Builder:

```java
.transition()
    .from(OrderState.PAYMENT_PENDING)
    .event(OrderEvent.PAYMENT_RESERVED)
    .to(OrderState.INVENTORY_PENDING)
    .updateContext((ctx, event) ->
            ctx.put("paymentReservationId", event.payload().reservationId()))
    .command(reserveInventory)
```

Если updater нет — context копируется без изменений.

Guards, updater и command factories получают один и тот же `TransitionContext`. Они обязаны быть чистыми: без I/O, без Kafka, без REST, без своей транзакции. Их вызывает engine внутри уже открытой DB-транзакции.

```java
public interface TransitionContext<S, E, P> {

    String machineType();

    String machineId();

    S currentState();

    StateMachineContext workflowContext();

    StateMachineEvent<E, P> event();

    P payload();
}
```

Для command factory `workflowContext()` — это уже результат updater, `currentState()` — state до перехода. Target state factory берёт из definition transition, не вычисляет сама.

```java
@FunctionalInterface
public interface StateMachineCommandFactory<S, E, P> {

    StateMachineCommand create(TransitionContext<S, E, P> ctx);
}
```

`.command(factory)` добавляет одну command. Несколько `.command(...)` на одном transition дают `0..N` commands.

## D2. Регистрация payload-класса на event type

`sendAsync()` кладёт payload в JSONB. Recovery worker должен восстановить типизированный `StateMachineEvent<E, P>`. Поэтому каждый event type definition обязан знать Java-класс payload.

Регистрация — на уровне definition, не на каждом transition:

```java
.payload(OrderEvent.START, Void.class)
.payload(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
```

Правила:

- каждый `event(...)` в transitions должен иметь зарегистрированный payload-класс;
- отсутствие регистрации — fail-fast в `build()`;
- `Void.class` означает `payload == null`;
- в `state_machine_request` пишется `event_type` как `enum.name()` и JSON payload;
- deserializer берёт класс только из definition registry, без полиморфного Jackson «на глаз»;
- неизвестный `event_type` при recovery — ошибка обработки request (retry/DEAD по общей retry-политике), не молчаливый skip.

`eventId` рекомендуется делать глобально уникальным UUID: PK `state_machine_event` — это `event_id` без `machineId`.

## D3. Queue SPI знает `machineId`; concurrent processing сериализуется advisory lock

Partition router не может маршрутизировать по одному `requestId`. SPI очереди фиксируется так:

```java
public interface StateMachineDispatchQueue {

    boolean offer(long requestId, String machineId);

    Long poll(int partition, Duration timeout) throws InterruptedException;

    int partitions();

    int size();

    int capacity();

    double pressure();
}
```

`offer` считает partition как `hash(machineId) % partitions()`. Worker `i` вызывает только `poll(i, ...)`.

Гарантии порядка:

| Граница | Гарантия |
|---|---|
| Один pod, async | События одного `machineId` идут в одну partition и обрабатываются последовательно |
| Несколько pod | Нет глобального FIFO. Параллельные transition одного instance запрещены |
| `send()` + `sendAsync()` на одном instance | Разрешено. Оба пути берут один и тот же advisory lock |

Перед чтением instance для обработки (и `send()`, и async worker) starter берёт transaction-scoped PostgreSQL advisory lock:

```text
pg_advisory_xact_lock(hashtext(machine_type || chr(0) || machine_id))
```

Lock живёт до COMMIT/ROLLBACK той транзакции, где выполняется transition.

Optimistic locking остаётся второй линией защиты: stale cache, ручной SQL, будущие реализации без lock не должны терять updates.

Memory queue по-прежнему fast path и локальна для pod. Recovery может отдать request другому pod; advisory lock не даст двум pod менять один instance одновременно.

## D4. `TransitionResult` вместо смеси exception/result; rejected пишется в `state_machine_event`

Ожидаемые исходы — значения `TransitionResult`, не исключения.

```java
public sealed interface TransitionResult
        permits TransitionResult.Success,
                TransitionResult.Duplicate,
                TransitionResult.Rejected {

    String machineType();
    String machineId();
    String eventId();
    String eventType();
    TransitionOutcome outcome();

    record Success(
            String machineType,
            String machineId,
            String eventId,
            String eventType,
            String fromState,
            String toState,
            long version,
            List<StateMachineCommand> commands
    ) implements TransitionResult {

        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.SUCCESS;
        }
    }

    record Duplicate(
            String machineType,
            String machineId,
            String eventId,
            String eventType,
            String fromState,
            String toState
    ) implements TransitionResult {

        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.DUPLICATE;
        }
    }

    record Rejected(
            String machineType,
            String machineId,
            String eventId,
            String eventType,
            String state,
            RejectedReason reason
    ) implements TransitionResult {

        @Override
        public TransitionOutcome outcome() {
            return TransitionOutcome.REJECTED;
        }
    }
}

public enum TransitionOutcome {
    SUCCESS,
    DUPLICATE,
    REJECTED
}

public enum RejectedReason {
    NO_TRANSITION,
    GUARD_NOT_MATCHED
}
```

Исключения — только инфраструктура и баги configuration:

| Ситуация | Контракт |
|---|---|
| Успешный переход | `Success` |
| Повтор того же `eventId` | `Duplicate`, без нового transition и без commands |
| Нет matching transition / все guards false | `Rejected`, instance не меняется |
| Два и более matching transition | `AmbiguousTransitionException` |
| Optimistic lock conflict | `OptimisticLockConflictException` |
| Неизвестный `machineType` | `UnknownMachineTypeException` |
| Ошибка persistence / payload deserialize | соответствующие unchecked exceptions |

Запись в `state_machine_event`:

| Исход | INSERT event | UPDATE instance | Commands | Async request |
|---|---|---|---|---|
| `SUCCESS` | да, `result = success`, `from_state` / `to_state` | да, `version + 1` | 0..N | `DONE` |
| `REJECTED` | да, `result = rejected`, `from_state = to_state = current` | нет | нет | `DONE` |
| `DUPLICATE` | нет, строка уже есть | нет | нет | `DONE` |

Rejected обязан попасть в event table. Иначе тот же `eventId` после правки definition может внезапно пройти. Повтор rejected `eventId` возвращает `Duplicate`.

`AmbiguousTransitionException` — дефект definition. Async request в этом случае не бизнес-`Rejected`: transition не коммитится, request не помечается `DONE`, действует обычная retry/DEAD политика.

---

# 4. Нефункциональные требования

| ID | Требование |
|---|---|
| NFR-1 | Java 21+ |
| NFR-2 | Spring Boot 4.1.x |
| NFR-3 | PostgreSQL — durable source of truth |
| NFR-4 | Потеря RAM/cache не приводит к потере committed state |
| NFR-5 | Event-level idempotency по `eventId` |
| NFR-6 | Concurrent transitions защищаются optimistic locking |
| NFR-7 | Durable `sendAsync()` переживает restart pod |
| NFR-8 | Core не зависит от Kafka/REST |
| NFR-9 | Line coverage library modules ≥ 85% |
| NFR-10 | Javadoc на public API |
| NFR-11 | Micrometer metrics |
| NFR-12 | Micrometer Observation / OpenTelemetry tracing |
| NFR-13 | Actuator health indicator |
| NFR-14 | Structured logging transitions |
| NFR-15 | Schema modes `create / validate / none` |
| NFR-16 | Несколько definitions в одном приложении |

---

# 5. Модули

```text
spring-boot-state-machine-starter-parent
|
+-- state-machine-core
|
+-- state-machine-persistence-jdbc
|
+-- state-machine-queue-memory
|
+-- state-machine-spring-boot-starter
|
+-- state-machine-demo
```

В следующих версиях:

```text
state-machine-outbox-integration
state-machine-queue-redis
state-machine-demo-saga
```

## 5.1. Назначение модулей

| Module | Назначение |
|---|---|
| `state-machine-core` | Definition, Event, Transition, Guard, Command, Engine, SPI |
| `state-machine-persistence-jdbc` | PostgreSQL persistence, optimistic locking, schema management |
| `state-machine-queue-memory` | Memory fast path для async processing |
| `state-machine-spring-boot-starter` | Auto-config, properties, cache, workers, metrics, health |
| `state-machine-demo` | Минимальный пример использования |

---

# 6. State Machine Definition

Definition создаётся приложением при старте.

Пример:

```java
@Bean
StateMachineDefinition<OrderState, OrderEvent> orderSaga(
        ReservePaymentCommandFactory reservePayment,
        ReserveInventoryCommandFactory reserveInventory,
        ReleasePaymentCommandFactory releasePayment) {

    return StateMachineDefinition
            .builder("order-saga", OrderState.class, OrderEvent.class)

            .initial(OrderState.NEW)

            .payload(OrderEvent.START, Void.class)
            .payload(OrderEvent.PAYMENT_RESERVED, PaymentReservedPayload.class)
            .payload(OrderEvent.PAYMENT_REJECTED, PaymentRejectedPayload.class)
            .payload(OrderEvent.INVENTORY_RESERVED, InventoryReservedPayload.class)
            .payload(OrderEvent.INVENTORY_REJECTED, InventoryRejectedPayload.class)
            .payload(OrderEvent.PAYMENT_RELEASED, Void.class)

            .transition()
                .from(OrderState.NEW)
                .event(OrderEvent.START)
                .to(OrderState.PAYMENT_PENDING)
                .command(reservePayment)

            .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_RESERVED)
                .to(OrderState.INVENTORY_PENDING)
                .updateContext((ctx, event) ->
                        ctx.put("paymentReservationId", event.payload().reservationId()))
                .command(reserveInventory)

            .transition()
                .from(OrderState.PAYMENT_PENDING)
                .event(OrderEvent.PAYMENT_REJECTED)
                .to(OrderState.CANCELLED)

            .transition()
                .from(OrderState.INVENTORY_PENDING)
                .event(OrderEvent.INVENTORY_RESERVED)
                .to(OrderState.COMPLETED)

            .transition()
                .from(OrderState.INVENTORY_PENDING)
                .event(OrderEvent.INVENTORY_REJECTED)
                .to(OrderState.PAYMENT_COMPENSATION_PENDING)
                .command(releasePayment)

            .transition()
                .from(OrderState.PAYMENT_COMPENSATION_PENDING)
                .event(OrderEvent.PAYMENT_RELEASED)
                .to(OrderState.CANCELLED)

            .build();
}
```

Definition хранится в памяти и не должен загружаться из PostgreSQL в v1.

Регистрация payload-классов обязательна (D2). Обновление context — только через `.updateContext(...)` (D1).

---

# 7. Transition

Формула transition:

```text
FROM
  +
EVENT
  +
optional GUARD
  |
  v
 TO
  +
optional CONTEXT UPDATE
  +
0..N COMMANDS
```

Пример:

```text
PAYMENT_PENDING
       +
PAYMENT_RESERVED
       |
       v
INVENTORY_PENDING
       |
       +-- ReserveInventoryCommand
       +-- NotifyCustomerCommand
       +-- NotifyAccountingCommand
```

Один transition может сформировать:

- 0 commands;
- 1 command;
- несколько независимых commands.

---

# 8. State Machine Instance

State machine не хранит бизнес-сущность.

Основная модель persistence/SPI:

```java
public record StateMachineInstance(
        String machineType,
        String machineId,
        String state,
        Map<String, Object> context,
        long version
) {}
```

Engine и application code работают с `StateMachineContext` (D1). `Map` — представление JSONB, не public mutator.

Пример:

```text
machineType = order-saga
machineId   = order-123
state       = INVENTORY_PENDING
version     = 4
```

Context:

```json
{
  "paymentReservationId": "PAY-789"
}
```

## 8.1. Что нельзя хранить как основной context

Не рекомендуется дублировать:

```text
customer
amount
items
deliveryAddress
full Order
full Payment
```

Эти данные принадлежат domain storage приложения.

## 8.2. Что допустимо хранить

Workflow-specific данные:

```text
paymentReservationId
inventoryReservationId
retryCount
compensationReason
externalOperationId
```

Идентификаторы лучше хранить строками: после JSONB Jackson не гарантирует `Long` vs `Integer`.

---

# 9. Event Model

Входящее событие:

```java
public record StateMachineEvent<E, P>(
        String eventId,
        String machineId,
        E type,
        P payload
) {}
```

Пример:

```java
var event = new StateMachineEvent<>(
        "evt-456",
        "order-123",
        OrderEvent.PAYMENT_RESERVED,
        new PaymentReservedPayload("PAY-789")
);
```

Семантика:

```text
machineId = какой instance state machine
eventId   = уникальный экземпляр события
type      = что произошло
payload   = данные конкретного события
```

`payload` существует для transition processing.

При необходимости данные из payload могут быть сохранены в workflow context через `updateContext` (D1).

Для `sendAsync()` payload сериализуется в JSONB по классу, зарегистрированному через `.payload(eventType, payloadClass)` (D2).

---

# 10. Idempotency

State machine должна обеспечивать собственную event-level idempotency.

Idempotency key:

```text
eventId
```

Не допускается считать универсальным ключом:

```text
machineId + eventType
```

Причина: одинаковое событие одного типа может легально произойти несколько раз.

Пример:

```text
eventId=ABC PAYMENT_TIMEOUT
eventId=DEF PAYMENT_TIMEOUT
```

Это два разных события.

Повтор:

```text
eventId=ABC PAYMENT_RESERVED
eventId=ABC PAYMENT_RESERVED
```

должен быть распознан как duplicate.

Повторный event не должен:

- повторно менять state;
- повторно создавать commands;
- повторно выполнять transition logic.

---

# 11. Public API

Основная входная точка:

```java
public interface StateMachineService {

    <E, P> TransitionResult send(
            String machineType,
            StateMachineEvent<E, P> event);

    <E, P> AsyncSubmission sendAsync(
            String machineType,
            StateMachineEvent<E, P> event);
}
```

Пример synchronous:

```java
stateMachineService.send(
        "order-saga",
        event
);
```

Пример asynchronous:

```java
stateMachineService.sendAsync(
        "order-saga",
        event
);
```

В дальнейшем допускается typed facade:

```java
orderSaga.send(event);

orderSaga.sendAsync(event);
```

В этом случае `machineType` скрывается от бизнес-кода.

`send()` возвращает `TransitionResult` (D4): `Success`, `Duplicate` или `Rejected`. Инфраструктурные сбои — исключения, не result.

`sendAsync()` durable-принимает событие и возвращает `AsyncSubmission` сразу после INSERT `state_machine_request`. Исход transition (`Success` / `Duplicate` / `Rejected`) определяется позже worker-ом и виден в history / metrics, не в возвращаемом значении `sendAsync()`.

---

# 12. Sync Processing

`send()` выполняет transition в caller thread.

```text
REST
KafkaListener
Scheduler
Java Service
     |
     v
StateMachineService.send()
     |
     v
TransitionEngine
     |
     v
Persistence
```

Пример Kafka consumer:

```java
@KafkaListener(topics = "payments.events")
public void onPaymentReserved(PaymentReserved message) {

    stateMachineService.send(
            "order-saga",
            new StateMachineEvent<>(
                    message.eventId(),
                    message.orderId(),
                    OrderEvent.PAYMENT_RESERVED,
                    message
            )
    );
}
```

Kafka остаётся вне starter-а.

---

# 13. Async Processing

`sendAsync()` означает:

> Starter durable-принял событие и самостоятельно отвечает за его последующую обработку.

Flow:

```text
Caller
  |
  v
sendAsync()
  |
  v
state_machine_request
  |
 COMMIT
  |
  v
Memory Queue
  |
  v
Partition Router
  |
  v
Worker
  |
  v
TransitionEngine
```

Memory queue является fast path.

PostgreSQL является durable source of truth.

---

# 14. Worker Model

Внутри одного pod:

```text
                 machineId
                    |
                    v
             Partition Router
                    |
       +------------+------------+
       |            |            |
       v            v            v
     Queue 0      Queue 1      Queue N
       |            |            |
       v            v            v
    Worker 0     Worker 1     Worker N
```

Требование:

> Все async events одного `machineId` внутри одного pod должны последовательно обрабатываться одной logical partition.

`offer(requestId, machineId)` обязателен (D3). Cross-pod concurrency сериализуется `pg_advisory_xact_lock` по `(machine_type, machine_id)`. Глобальный FIFO между pod не гарантируется.

Смешивать `send()` и `sendAsync()` на одном instance разрешено: оба пути берут один advisory lock.

Пример:

```yaml
state-machine:
  async:
    enabled: true
    workers: 8
    queue-capacity: 10000
```

Worker не должен в рекомендуемом Saga-сценарии блокироваться на удалённых REST/Kafka операциях.

Для этого transition создаёт Command.

---

# 15. Transition Engine

Core interface:

```java
public interface TransitionEngine {

    TransitionResult transition(
            StateMachineDefinition<?, ?> definition,
            StateMachineInstance instance,
            StateMachineEvent<?, ?> event);
}
```

Ответственность:

1. определить current state;
2. найти transition;
3. проверить guard;
4. проверить отсутствие ambiguity;
5. вычислить target state;
6. применить `ContextUpdater`, если задан;
7. сформировать commands через command factories;
8. вернуть `TransitionResult` (`Success` или `Rejected`).

Engine не выполняет persistence, idempotency lookup и publish commands. Это делает `StateMachineService` вокруг engine.

`Duplicate` engine не возвращает: его определяет event store до вызова engine.

Engine не знает о:

- Kafka;
- REST;
- PostgreSQL implementation;
- Outbox implementation;
- конкретных бизнес-сервисах.

---

# 16. Guard

Guard определяет, разрешён ли конкретный transition.

Пример:

```java
.transition()
    .from(PAYMENT_PENDING)
    .event(PAYMENT_RESERVED)
    .when(ctx -> ctx.payload().amount().signum() > 0)
    .to(INVENTORY_PENDING)
```

Guard получает `TransitionContext` (D1) и обязан быть чистым.

Для одной пары:

```text
state + event
```

могут существовать несколько transitions с разными guards.

После вычисления guards должен быть найден максимум один допустимый transition.

Если допустимы два и более transition — ошибка configuration/runtime ambiguity.

---

# 17. Commands

`Command` описывает:

> Что необходимо сделать после успешного transition.

```java
public interface StateMachineCommand {

    String type();

    Object payload();
}
```

```java
public record ReserveInventoryCommand(
        String orderId,
        String paymentReservationId
) implements StateMachineCommand {}
```

Пример factory:

```java
public class ReserveInventoryCommandFactory
        implements StateMachineCommandFactory<OrderState, OrderEvent, PaymentReservedPayload> {

    @Override
    public StateMachineCommand create(
            TransitionContext<OrderState, OrderEvent, PaymentReservedPayload> ctx) {

        return new ReserveInventoryCommand(
                ctx.machineId(),
                ctx.workflowContext().getString("paymentReservationId").orElseThrow()
        );
    }
}
```

Factory обязана быть чистой (D1). I/O и публикация — только `CommandPublisher` после успешного persist.

Семантика:

```text
EVENT
  |
  v
State Machine
  |
  v
Transition
  |
  v
New State
  |
  +--> Command
  +--> Command
  +--> Command
```

Command не означает конкретный transport.

Один и тот же Command концептуально может быть выполнен:

- через Kafka;
- через REST;
- через application adapter;
- через Outbox.

---

# 18. Command Publisher

Core SPI:

```java
public interface StateMachineCommandPublisher {

    void publish(
            StateMachineInstance machine,
            Collection<StateMachineCommand> commands);
}
```

Возможные реализации:

```text
NoOpCommandPublisher
ApplicationCommandPublisher
OutboxCommandPublisher
```

Для distributed Saga рекомендуемый production вариант:

```text
StateMachine
     |
     v
Commands
     |
     v
OutboxCommandPublisher
     |
     v
spring-boot-outbox-starter
     |
     v
Kafka / REST
```

---

# 19. Transaction Boundary

Для state transition и distributed commands требуется возможность атомарного commit.

```text
BEGIN

advisory lock (D3)

UPDATE state_machine_instance   -- только для Success

INSERT state_machine_event      -- Success и Rejected

INSERT outbox command #1        -- только для Success
INSERT outbox command #2

COMMIT
```

Таким образом:

```text
State Machine
    = что необходимо сделать

Outbox
    = как гарантированно доставить

Consumer Idempotency
    = как безопасно выполнить повтор
```

---

# 20. Persistence Schema

## 20.1. `state_machine_instance`

Текущее durable состояние.

```sql
CREATE TABLE state_machine_instance (
    machine_type VARCHAR(128) NOT NULL,
    machine_id   VARCHAR(128) NOT NULL,

    state        VARCHAR(128) NOT NULL,
    context      JSONB,

    version      BIGINT NOT NULL DEFAULT 0,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (machine_type, machine_id)
);
```

---

# 21. Optimistic Locking

Transition update:

```sql
UPDATE state_machine_instance
SET state = ?,
    context = ?,
    version = version + 1,
    updated_at = NOW()
WHERE machine_type = ?
  AND machine_id = ?
  AND version = ?;
```

Если:

```text
updatedRows == 0
```

starter должен считать это concurrent transition conflict.

Метрика:

```text
state_machine_optimistic_lock_conflict_total
```

Policy retry может быть configurable в следующих версиях.

---

# 22. `state_machine_event`

Таблица одновременно используется для:

- idempotency;
- transition history;
- audit/debugging.

```sql
CREATE TABLE state_machine_event (
    event_id      VARCHAR(128) NOT NULL,

    machine_type  VARCHAR(128) NOT NULL,
    machine_id    VARCHAR(128) NOT NULL,

    event_type    VARCHAR(128) NOT NULL,

    from_state    VARCHAR(128) NOT NULL,
    to_state      VARCHAR(128) NOT NULL,

    result        VARCHAR(32) NOT NULL,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (event_id),

    CONSTRAINT chk_state_machine_event_result
        CHECK (result IN ('success', 'rejected'))
);
```

`result` обязателен (D4):

- `success` — state изменился, `from_state` / `to_state` отражают переход;
- `rejected` — state не изменился, `from_state = to_state`.

Duplicate в эту таблицу не пишется: строка с тем же `event_id` уже существует.

Индекс:

```sql
CREATE INDEX idx_state_machine_event_machine
ON state_machine_event(
    machine_type,
    machine_id,
    created_at
);
```

---

# 23. `state_machine_request`

Используется только для durable `sendAsync()`.

```sql
CREATE TABLE state_machine_request (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY,

    event_id       VARCHAR(128) NOT NULL,

    machine_type   VARCHAR(128) NOT NULL,
    machine_id     VARCHAR(128) NOT NULL,
    event_type     VARCHAR(128) NOT NULL,

    payload        JSONB,

    status         INT NOT NULL,

    retry_count    INT NOT NULL DEFAULT 0,

    locked_by      VARCHAR(128),
    locked_until   TIMESTAMPTZ,

    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ,

    PRIMARY KEY (id),

    UNIQUE (event_id)
);
```

Предварительные статусы:

```text
NEW        = 0
PROCESSING = 1
FAILED     = 2

DONE       = 100
DEAD       = 101
```

---

# 24. Назначение таблиц

```text
state_machine_instance
    |
    +-- где процесс находится сейчас


state_machine_event
    |
    +-- какие события уже обработаны
    +-- история transitions


state_machine_request
    |
    +-- какие async events ещё необходимо обработать
```

---

# 25. Async Submission Flow

```text
BEGIN

INSERT state_machine_request
status = NEW

COMMIT
```

После commit:

```text
MemoryQueue.offer(requestId, machineId)
```

Если:

```text
offer == false
```

событие не считается потерянным.

Recovery worker позже подберёт request из PostgreSQL.

---

# 26. Async Worker Flow

```text
Worker
  |
  v
claim request
  |
  v
check eventId
  |
  v
load machine
  |
  v
TransitionEngine
  |
  v
persist state
  |
  v
insert event history
  |
  v
publish commands
  |
  v
request = DONE
```

Основная DB transaction:

```text
BEGIN

advisory lock (D3)

claim state_machine_request

check duplicate event          -- если да: request = DONE, COMMIT, Duplicate

engine.transition(...)

UPDATE state_machine_instance  -- только Success

INSERT state_machine_event     -- Success и Rejected

publish transactional Commands -- только Success

UPDATE state_machine_request
SET status = DONE              -- Success, Duplicate, Rejected

COMMIT
```

`Rejected` и `Duplicate` завершают request как `DONE`. Их нельзя ретраить как ошибку worker-а.

---

# 27. Recovery

Recovery обрабатывает:

```text
NEW
FAILED
expired PROCESSING lease
```

Flow:

```text
RecoveryWorker
      |
      v
PostgreSQL
      |
      v
claim recoverable IDs
      |
      v
Memory Queue
      |
      v
normal Worker
```

Инвариант:

> Recovery не выполняет transition напрямую.

Он возвращает work item в основной processing pipeline.

---

# 28. RAM Cache

RAM является только cache/hot working set.

```text
MachineCache

order-saga:101 -> PAYMENT_PENDING
order-saga:102 -> INVENTORY_PENDING
order-saga:103 -> COMPENSATING
```

Configuration:

```yaml
state-machine:
  cache:
    enabled: true
    max-size: 100000
    expire-after-access: 30m
```

Lookup:

```text
machine key
    |
    v
Cache
  |
  +-- HIT --> Transition
  |
  +-- MISS --> PostgreSQL
                  |
                  v
                Cache
                  |
                  v
              Transition
```

Eviction не выполняет persistence.

После каждого successful transition новый state уже должен быть durable в PostgreSQL.

Completed instances допускается удалять из cache сразу.

---

# 29. Schema Management

Starter должен поддерживать:

```yaml
state-machine:
  persistence:
    schema:
      mode: validate
```

Режимы:

| Mode | Поведение |
|---|---|
| `create` | Создать отсутствующие таблицы |
| `validate` | Проверить schema и fail-fast при несовместимости |
| `none` | Не управлять schema |

Для demo/test:

```yaml
state-machine:
  persistence:
    schema:
      mode: create
```

Для production:

```yaml
state-machine:
  persistence:
    schema:
      mode: validate
```

или:

```yaml
mode: none
```

при Flyway/Liquibase.

DDL:

```text
state-machine-persistence-jdbc
└── src/main/resources
    └── state-machine-schema.sql
```

Manager:

```java
StateMachineSchemaManager
```

---

# 30. Schema Validation

`validate` должен проверять минимум:

- наличие всех обязательных таблиц;
- обязательные columns;
- типы критичных columns;
- primary key `state_machine_instance`;
- unique constraint на `event_id`;
- наличие `result` и CHECK `success|rejected` на `state_machine_event`;
- наличие `version`;
- наличие async lease columns;
- наличие индексов, необходимых recovery.

При несовместимости startup должен завершаться fail-fast с понятным сообщением.

---

# 31. Metrics

## 31.1. Transitions

Counter:

```text
state_machine_transition_total{
    machineType,
    fromState,
    toState,
    eventType,
    result
}
```

`result`:

```text
success
rejected
duplicate
error
```

Timer:

```text
state_machine_transition_seconds{
    machineType,
    eventType
}
```

---

# 32. Async Metrics

```text
state_machine_async_submitted_total

state_machine_async_processed_total

state_machine_async_failed_total

state_machine_async_recovery_total

state_machine_queue_size

state_machine_queue_pressure
```

Допустимые tags:

```text
machineType
result
```

Worker number может использоваться только там, где это действительно полезно и не создаёт лишнюю cardinality.

---

# 33. Cache Metrics

```text
state_machine_cache_size

state_machine_cache_hit_total

state_machine_cache_miss_total

state_machine_cache_eviction_total
```

---

# 34. Persistence Metrics

```text
state_machine_load_seconds

state_machine_persist_seconds

state_machine_optimistic_lock_conflict_total
```

---

# 35. State Distribution

Gauge:

```text
state_machine_state_count{
    machineType,
    state
}
```

Пример:

```text
order-saga / PAYMENT_PENDING       = 120
order-saga / INVENTORY_PENDING     = 42
order-saga / COMPENSATING          = 7
```

Метрика позволяет находить накопление/зависание процессов на конкретном state.

---

# 36. Metric Cardinality Rules

Запрещено использовать как metric tags:

```text
machineId
eventId
payload values
external IDs
```

Допустимы:

```text
machineType
eventType
state
result
```

---

# 37. Distributed Tracing

Использовать:

```text
Micrometer Observation
OpenTelemetry
```

Каждый transition создаёт span:

```text
state-machine.transition
```

Attributes:

```text
state.machine.type
state.machine.id
state.event.id
state.event.type
state.from
state.to
state.result
```

`machineId` и `eventId` допустимы как trace attributes, но запрещены как metric tags.

---

# 38. Trace Propagation

Для integration с Outbox должен поддерживаться propagation trace context.

Ожидаемый distributed trace:

```text
Kafka consume
      |
      v
state-machine.transition
      |
      v
outbox.append
      |
      v
outbox.publish
      |
      v
Kafka produce
      |
      v
External Service
```

Ответ:

```text
External Service
      |
      v
Kafka result
      |
      v
Kafka consume
      |
      v
state-machine.transition
```

---

# 39. Structured Logging

Transition log должен содержать:

```text
machineType
machineId
eventId
eventType
fromState
toState
result
duration
```

Пример:

```json
{
  "machineType": "order-saga",
  "machineId": "order-123",
  "eventId": "evt-456",
  "eventType": "PAYMENT_RESERVED",
  "fromState": "PAYMENT_PENDING",
  "toState": "INVENTORY_PENDING",
  "result": "success"
}
```

`machineId` используется как основной correlation ID жизненного цикла workflow.

---

# 40. Health Indicator

Actuator indicator:

```text
stateMachine
```

Минимальные details:

```text
cacheSize
queueSize
queuePressure
asyncWorkers
recoveryEnabled
oldestPendingRequestAge
```

Queue overflow сам по себе не должен переводить health в `DOWN`, если durable request сохранён и recovery работает.

Критический `DOWN/OUT_OF_SERVICE` возможен при невозможности:

- читать durable state;
- сохранять transitions;
- обрабатывать durable requests.

---

# 41. Timeout Handling

Timeout рассматривается как Event.

Например:

```text
PAYMENT_PENDING
deadline exceeded
      |
      v
PAYMENT_TIMEOUT
```

Scheduler приложения либо будущий timer module вызывает:

```java
stateMachineService.sendAsync(
        "order-saga",
        paymentTimeoutEvent
);
```

Definition решает дальнейшее действие:

```text
PAYMENT_PENDING
      |
      | PAYMENT_TIMEOUT
      v
PAYMENT_COMPENSATION_PENDING
```

или retry.

Generic durable timer engine — out of scope v1.

---

# 42. Saga Reference Example

Reference project должен показать orchestration:

```text
START
  |
  v
PAYMENT_PENDING
  |
  | PaymentReservedEvent
  v
INVENTORY_PENDING
  |
  +--> ReserveInventoryCommand
  +--> NotifyPaymentCommand
  |
  | InventoryReservedEvent
  v
COMPLETED
```

Failure:

```text
INVENTORY_PENDING
       |
       | InventoryRejectedEvent
       v
PAYMENT_COMPENSATION_PENDING
       |
       +--> ReleasePaymentCommand
       |
       | PaymentReleasedEvent
       v
CANCELLED
```

---

# 43. Saga External Interaction

Payment Service:

```text
State Machine
     |
     | ReservePaymentCommand
     v
Outbox
     |
     v
Kafka
     |
     v
Payment Service
     |
     | PaymentReservedEvent
     v
Kafka
     |
     v
State Machine
```

Inventory:

```text
State Machine
     |
     | ReserveInventoryCommand
     v
Kafka
     |
     v
Inventory Service
     |
     | InventoryReserved
     | InventoryRejected
     v
State Machine
```

Notification commands могут выполняться независимо и не обязаны блокировать business completion Saga.

---

# 44. Compensation

Компенсация является частью business definition.

Например:

```text
INVENTORY_REJECTED
       |
       v
PAYMENT_COMPENSATION_PENDING
       |
       +--> ReleasePaymentCommand
       |
       v
PAYMENT_RELEASED
       |
       v
CANCELLED
```

State machine framework не должен автоматически угадывать compensating command.

Его явно определяет приложение.

---

# 45. Основные архитектурные инварианты

1. PostgreSQL — durable source of truth.
2. RAM — cache, а не storage.
3. Потеря cache не приводит к потере committed transition.
4. Definition хранится в приложении.
5. Business entity не хранится внутри state machine.
6. Context содержит только workflow-specific данные.
7. `eventId` является idempotency key входного события.
8. Один `eventId` не может быть обработан как новый transition дважды: и `success`, и `rejected` потребляют ключ.
9. `machineId + eventType` не является универсальным idempotency key.
10. Transition определяется `from + event + optional guard`.
11. Transition может создать `0..N Commands`.
12. Command описывает intent, а не transport.
13. Core не зависит от Kafka.
14. Core не зависит от REST.
15. Для distributed commands рекомендуемый путь — Transactional Outbox.
16. State transition + event record + transactional commands должны фиксироваться атомарно.
17. Async memory queue — fast path.
18. `state_machine_request` — durable async source of truth.
19. Recovery возвращает request в normal processing pipeline.
20. Concurrent transitions защищаются optimistic locking; обработка одного instance сериализуется advisory lock (D3).
21. `machineId` и `eventId` запрещены как metric tags.
22. Compensation определяется application definition.
23. Timeout рассматривается как Event.
24. External call не должен блокировать worker в рекомендуемой async Saga-модели.
25. Context мутируется только через `ContextUpdater` / `StateMachineContext` (D1).
26. Каждый event type в definition имеет зарегистрированный payload-класс (D2).
27. Dispatch queue маршрутизирует по `machineId`; cross-pod сериализация — advisory lock (D3).
28. Ожидаемые исходы `send()` — `TransitionResult`, не exception (D4).
29. Rejected event записывается в `state_machine_event` и становится idempotent по `eventId` (D4).

---

# 46. Configuration Draft

```yaml
state-machine:
  enabled: true

  instance-id: ${HOSTNAME:local}

  persistence:
    schema:
      mode: validate

  cache:
    enabled: true
    max-size: 100000
    expire-after-access: 30m

  async:
    enabled: true
    workers: 8

    queue:
      capacity: 10000

    lease-duration: 30s
    max-retries: 5

    recovery:
      enabled: true
      interval: 10s
      batch-size: 500

  observability:
    metrics:
      enabled: true

    tracing:
      enabled: true

    health:
      enabled: true
```

---

# 47. Auto-configuration

Starter должен:

1. найти все `StateMachineDefinition` beans;
2. зарегистрировать definitions в registry;
3. создать JDBC repositories;
4. применить schema mode;
5. поднять cache;
6. создать async queue;
7. поднять worker pool;
8. поднять recovery worker;
9. создать `StateMachineService`;
10. зарегистрировать Micrometer metrics;
11. зарегистрировать Observation instrumentation;
12. зарегистрировать Actuator health indicator.

---

# 48. Registry

```java
public interface StateMachineRegistry {

    StateMachineDefinition<?, ?> getRequired(String machineType);

    Optional<StateMachineDefinition<?, ?>> find(String machineType);

    Collection<StateMachineDefinition<?, ?>> all();
}
```

Duplicate `machineType` при startup должен приводить к fail-fast.

---

# 49. Persistence SPI

```java
public interface StateMachineStore {

    Optional<StateMachineInstance> find(
            String machineType,
            String machineId);

    StateMachineInstance create(...);

    boolean update(
            StateMachineInstance instance,
            long expectedVersion);
}
```

Event:

```java
public interface StateMachineEventStore {

    boolean exists(String eventId);

    void append(ProcessedStateMachineEvent event);
}
```

Async:

```java
public interface StateMachineRequestStore {

    long append(StateMachineRequest request);

    List<StateMachineRequest> claim(...);

    void markDone(...);

    void markFailed(...);
}
```

---

# 50. Cache SPI

```java
public interface StateMachineCache {

    Optional<StateMachineInstance> get(
            String machineType,
            String machineId);

    void put(StateMachineInstance instance);

    void invalidate(
            String machineType,
            String machineId);
}
```

---

# 51. Queue SPI

Контракт зафиксирован в D3. Кратко:

```java
public interface StateMachineDispatchQueue {

    boolean offer(long requestId, String machineId);

    Long poll(int partition, Duration timeout) throws InterruptedException;

    int partitions();

    int size();

    int capacity();

    double pressure();
}
```

Memory implementation является default в v1. `offer` без `machineId` не допускается.

---

# 52. Обработка duplicate event

Flow:

```text
Event
  |
  v
eventId already processed?
       |
   +---+---+
   |       |
  YES      NO
   |       |
   v       v
duplicate transition
result     processing
```

Duplicate не вставляет новую строку в `state_machine_event`. Исход предыдущей обработки (`success` или `rejected`) уже зафиксирован.

`Duplicate` не является технической ошибкой. Он отражается как `result = duplicate` в metrics/logs/traces.

---

# 53. Invalid Transition

Например:

```text
current state = COMPLETED
event         = PAYMENT_RESERVED
```

Definition не содержит transition.

`send()` возвращает `TransitionResult.Rejected` с `RejectedReason.NO_TRANSITION`.

Если transitions есть, но все guards false — `RejectedReason.GUARD_NOT_MATCHED`.

В обоих случаях:

- instance и version не меняются;
- commands не публикуются;
- в `state_machine_event` пишется строка `result = rejected`, `from_state = to_state`;
- повтор того же `eventId` даёт `Duplicate`.

`TransitionRejectedException` в public API v1 нет.

---

# 54. Testing Strategy

## Unit Tests

Покрыть:

- definition builder;
- initial state;
- transition lookup;
- guard true/false;
- multiple guards;
- ambiguity detection;
- invalid transition;
- context mutation через `updateContext` / `StateMachineContext`;
- payload registry и fail-fast `build()` без `.payload(...)`;
- 0 commands;
- 1 command;
- multiple commands;
- command factory читает обновлённый context;
- duplicate event → `TransitionResult.Duplicate`;
- invalid transition → `TransitionResult.Rejected`;
- rejected event повторно даёт `Duplicate`;
- cache hit;
- cache miss;
- partition routing;
- metrics recording;
- tracing instrumentation.

---

# 55. JDBC Integration Tests

Testcontainers PostgreSQL:

- schema create;
- schema validate;
- schema none;
- create instance;
- load instance;
- optimistic update;
- concurrent update conflict;
- event insert success;
- event insert rejected;
- duplicate event;
- duplicate after rejected;
- history query;
- async request insert;
- payload JSONB round-trip;
- request claim;
- lease expiration;
- failed request;
- recovery;
- mark DONE;
- mark DEAD.

---

# 56. Concurrency Tests

Обязательный сценарий:

```text
same machineId
     +
2 concurrent events
     |
     v
no lost update
```

Проверить optimistic locking и `pg_advisory_xact_lock` на одном `(machine_type, machine_id)` из двух параллельных обработчиков.

Async ordering:

```text
event A
event B
event C
```

для одного `machineId` должны обрабатываться последовательно внутри одного pod.

Cross-pod: concurrent transition одного instance не допускается; глобальный FIFO не требуется.

---

# 57. Recovery Tests

Crash-like сценарий:

```text
request persisted
       |
       v
memory queue lost
       |
       v
application/recovery starts
       |
       v
request processed
```

Также:

```text
PROCESSING + expired lease
```

должен быть recoverable.

---

# 58. Observability Tests

Проверить:

- transition counter;
- duplicate counter/result;
- rejected result;
- transition timer;
- cache hit/miss;
- queue pressure;
- recovery counter;
- optimistic lock metric;
- transition span;
- expected span attributes;
- отсутствие `machineId/eventId` в metric tags.

---

# 59. Architecture Tests

ArchUnit:

- `state-machine-core` не зависит от Spring Kafka;
- `state-machine-core` не зависит от JDBC implementation;
- persistence зависит от core SPI, а не наоборот;
- queue implementation зависит от queue SPI;
- Outbox integration не протекает в core;
- demo может зависеть от всех необходимых modules.

---

# 60. README Requirements

README должен быть на английском.

Структура:

1. Badges
2. Short pitch
3. Why this project
4. Core concepts
5. Architecture
6. Quick Start
7. Defining a State Machine
8. `send()` vs `sendAsync()` and `TransitionResult`
9. Events and idempotency
10. Commands
11. PostgreSQL persistence
12. Cache
13. Async workers
14. Recovery
15. Schema management
16. Metrics and tracing
17. Integration with Outbox
18. Saga example
19. Requirements
20. License

---

# 61. Positioning

Рабочее позиционирование:

> Lightweight persistent State Machine for Spring Boot with PostgreSQL durability, synchronous and asynchronous events, in-memory hot state cache, event idempotency and command-based workflow orchestration.

Для Saga:

```text
State Machine
     +
Outbox
     +
Idempotency
     +
Kafka
     =
Saga Orchestrator
```

---

# 62. Out of Scope v1

- BPMN
- hierarchical states
- parallel regions
- automatic fork/join
- distributed timer engine
- Redis queue
- Redis persistence
- Kafka-specific state machine module
- REST-specific state machine module
- DB-driven definitions
- workflow UI
- arbitrary scripting
- full workflow variable engine
- automatic Saga compensation inference
- Redis/ZooKeeper distributed locks как primary concurrency model

PostgreSQL `pg_advisory_xact_lock` входит в v1 как сериализация обработки одного instance (D3). Основная защита от lost update — по-прежнему optimistic locking.

---

# 63. План поставки

| Phase | Deliverable |
|---|---|
| P0 | Repo skeleton, parent POM, CI, license |
| P1 | `state-machine-core`: model, definitions, `StateMachineContext`, payload registry, `TransitionResult`, engine, command factories |
| P2 | JDBC instance persistence + schema manager |
| P3 | Event idempotency/history |
| P4 | Synchronous `send()` |
| P5 | RAM cache |
| P6 | Durable async request |
| P7 | Memory queue + worker pool |
| P8 | Recovery + leases |
| P9 | Metrics, tracing, health |
| P10 | Command SPI |
| P11 | Optional Outbox integration |
| P12 | Demo + README |
| P13 | Saga reference example |

---

# 64. Definition of Done v1

- [ ] Repository builds in CI.
- [ ] Java 21+ / Spring Boot 4.1.
- [ ] Multiple definitions can be registered.
- [ ] Duplicate `machineType` fails startup.
- [ ] Machine instance persists in PostgreSQL.
- [ ] `send()` works.
- [ ] Durable `sendAsync()` works.
- [ ] Events support `eventId`.
- [ ] Duplicate event is idempotently ignored.
- [ ] `from + event + guard + to` supported.
- [ ] Transition supports `0..N Commands`.
- [ ] Workflow context supported via `StateMachineContext` and `updateContext`.
- [ ] Event payload classes registered on definition; async deserialize works.
- [ ] Dispatch queue `offer(requestId, machineId)` routes by partition.
- [ ] Cross-pod processing of one instance is serialized by advisory lock.
- [ ] `send()` returns `TransitionResult` (`Success` / `Duplicate` / `Rejected`).
- [ ] Rejected events are persisted and subsequent same `eventId` is `Duplicate`.
- [ ] Optimistic locking works.
- [ ] Cache bounded by size/TTL.
- [ ] Cache miss restores state from PostgreSQL.
- [ ] Async worker pool works.
- [ ] Events of one machine preserve ordering within execution partition.
- [ ] Queue overflow does not lose durable request.
- [ ] Recovery works.
- [ ] Expired leases recover.
- [ ] Schema `create` works.
- [ ] Schema `validate` works.
- [ ] Schema `none` works.
- [ ] Metrics implemented.
- [ ] Distributed tracing implemented.
- [ ] Structured logging implemented.
- [ ] Health indicator implemented.
- [ ] Integration tests use Testcontainers.
- [ ] Library modules have ≥85% line coverage.
- [ ] README complete and in English.
- [ ] Minimal demo included.
- [ ] Outbox integration documented.
- [ ] Saga reference scenario documented or delivered as next milestone.

---

# 65. Итоговая архитектура

```text
                       APPLICATION
                            |
             +--------------+--------------+
             |                             |
           send()                      sendAsync()
             |                             |
             |                    state_machine_request
             |                             |
             |                         Memory Queue
             |                             |
             |                       Partition Router
             |                             |
             |                          Workers
             |                             |
             +-------------+---------------+
                           |
                           v
                  StateMachineService
                           |
                           v
                    TransitionEngine
                           |
             +-------------+-------------+
             |                           |
             v                           v
       Machine Cache               Event Dedup
             |                           |
       HIT / MISS                       eventId
             |
             v
        PostgreSQL
             |
       +-----+-------------------------------+
       |                                     |
       v                                     v
state_machine_instance              state_machine_event
       |
       | transition
       |
       +---------------------+
                             |
                             v
                        0..N Commands
                             |
                             v
                    CommandPublisher SPI
                             |
                   +---------+---------+
                   |                   |
                   v                   v
            Application             Outbox
                                      |
                                      v
                                Kafka / REST
```

Главная идея проекта:

> **Event → durable state transition → commands.**

При этом:

```text
PostgreSQL
    = durable state

RAM
    = hot working set

eventId
    = event idempotency

Command
    = what should happen next

Outbox
    = reliable command delivery

Saga
    = one of the primary reference use cases
```