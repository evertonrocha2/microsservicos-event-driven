# Microsserviços Event-Driven

Demonstração executável dos padrões de comunicação assíncrona em microsserviços:
mensageria, message broker, garantia de ordenação, idempotência, transactional
outbox e o padrão Sagas com transações compensatórias.

Quatro serviços Spring Boot, RabbitMQ e PostgreSQL, tudo rodando em containers.
Cada padrão está implementado de verdade e pode ser observado em execução.

---

## Os quatro serviços

| Serviço | Porta | Papel na arquitetura |
|---|---|---|
| `order-service` | 9081 | API REST, agregado `Order`, **orquestrador da Saga**, **Transactional Outbox** |
| `payment-service` | 9082 | Participante da saga, **consumidor idempotente** (tabela de inbox) |
| `inventory-service` | 9083 | Participante da saga, **garantia de ordenação**, **compensação** |
| `notification-service` | 9084 | Dois assinantes **pub/sub** e um exemplo de **chamada síncrona** |

Infraestrutura: RabbitMQ 3.13 e PostgreSQL 16, ambos via Docker.

---

## Como rodar

### 1. Subir a infraestrutura

```bash
docker compose up -d rabbitmq postgres
```

Console do RabbitMQ: <http://localhost:15672> (usuário `tp3`, senha `tp3`)

O PostgreSQL é publicado na porta **5433** do host para não conflitar com uma
instalação local na 5432.

### 2. Compilar

Precisa de JDK 21. Se o `java` do PATH for outro, aponte o `JAVA_HOME`:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot"
mvn -DskipTests package
```

### 3. Subir os serviços

Quatro terminais, um por serviço:

```bash
java -jar order-service/target/order-service-1.0.0.jar
java -jar payment-service/target/payment-service-1.0.0.jar
java -jar inventory-service/target/inventory-service-1.0.0.jar
java -jar notification-service/target/notification-service-1.0.0.jar
```

Rodando pela IDE, basta dar run nas quatro classes `*Application`.

Alternativa em containers (dispensa JDK e Maven no host):

```bash
docker compose --profile apps up --build
```

As portas no host são as mesmas (9081 a 9084). A primeira build leva alguns
minutos, porque cada imagem compila o módulo dentro do container.

---

## Documentação das APIs

**REST (síncrona)**, via Swagger UI:

- <http://localhost:9081/swagger-ui.html> (pedidos)
- <http://localhost:9082/swagger-ui.html> (pagamentos e inbox de idempotência)
- <http://localhost:9083/swagger-ui.html> (estoque, inbox e sequências)
- <http://localhost:9084/swagger-ui.html> (notificações e chamada síncrona)

**Mensagens (assíncrona)**: os contratos ficam no módulo `shared-contracts`,
em `commands/`, `events/` e `replies/`. A topologia (exchanges, filas e bindings)
é declarada nas classes `RabbitMQConfig` de cada serviço.

---

## Roteiro de demonstração

### Cenário 1: saga completa com sucesso

```bash
curl -X POST http://localhost:9081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-001","items":[{"sku":"SKU-MOUSE","quantity":2,"unitPrice":150.00}]}'
```

Resposta imediata: **HTTP 202 Accepted**, status `PENDING`. Alguns segundos
depois, consultando `GET /api/orders/{id}`, o status é `CONFIRMED`.

Percurso: `PENDING` → `INVENTORY_RESERVED` → `PAYMENT_AUTHORIZED` → `CONFIRMED`

### Cenário 2: saga compensada (pagamento recusado)

Valor acima de R$ 10.000 é recusado pelo `payment-service`.

```bash
curl -X POST http://localhost:9081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-002","items":[{"sku":"SKU-NOTEBOOK","quantity":5,"unitPrice":3500.00}]}'
```

O estoque é reservado, o pagamento é negado, a compensação devolve o estoque e o
pedido termina em `CANCELLED`. Confira o saldo antes e depois em
`GET http://localhost:9083/api/inventory/stock`.

### Cenário 3: falha no primeiro passo (estoque insuficiente)

```bash
curl -X POST http://localhost:9081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cliente-003","items":[{"sku":"SKU-RARO","quantity":5,"unitPrice":99.00}]}'
```

`SKU-RARO` só tem 1 unidade. A saga falha no passo 1 e não há nada a compensar.

### Cenário 4: síncrono contra assíncrono

```bash
# funciona com o order-service no ar
curl http://localhost:9084/api/notifications/sync/{orderId}

# derrube o order-service e repita: HTTP 503 na hora
# mas isto continua respondendo, porque os eventos já foram consumidos:
curl http://localhost:9084/api/notifications
```

Derrube o `notification-service`, crie pedidos e veja as mensagens se acumularem
na fila `notification.order-events.queue` no console do RabbitMQ. Ao subir o
serviço de novo, ele consome tudo que ficou esperando.

### Cenário 5: idempotência

Reenvie uma mensagem já processada pelo console do RabbitMQ (aba Exchanges,
`tp3.commands`, routing key `payment.authorize`, com o mesmo `messageId`).
O log mostra `DUPLICATA detectada` e nenhuma cobrança nova aparece em
`GET http://localhost:9082/api/payments`.

### Cenário 6: ordenação

Publique um `inventory.release` com `sequenceNumber: 2` para um `orderId` que
nunca teve reserva. A mensagem não é processada. Ela vai para
`inventory.release.retry.queue`, espera 3 segundos, volta, tenta de novo, e após
10 rodadas termina na DLQ.

O ponto importante: durante todo esse tempo a fila principal continua atendendo
os outros pedidos. Crie um pedido acima de R$ 10.000 em paralelo e veja a
compensação dele rodar normalmente.

---

## Estrutura do código

```
shared-contracts/     Contratos das mensagens (Published Language)
order-service/        Orquestrador da saga + outbox transacional
payment-service/      Consumidor idempotente
inventory-service/    Ordenação por agregado + compensação
notification-service/ Pub/sub e comparação síncrono x assíncrono
infra/                Script de criação dos bancos
```

Cada serviço segue camadas de DDD: `domain`, `application`, `infrastructure`
e `interfaces`.
