<!-- PLACE THIS FILE AT: mpesa-ai-agent/agent-service/README.md
     Replace the existing file if there is one. The SVG images it uses go in mpesa-ai-agent/docs/assets/. You can delete this comment. -->

<div align="center">

<img src="../docs/assets/banner-agent.svg" alt="" width="100%"/>

<br/>

<img alt="Spring%20Boot" src="https://img.shields.io/badge/Spring%20Boot-4.1.2-087F36?style=flat-square&logo=springboot&logoColor=white"/> <img alt="Java" src="https://img.shields.io/badge/Java-21-14231C?style=flat-square&logo=openjdk&logoColor=2BD576"/> <img alt="Maven" src="https://img.shields.io/badge/Maven-3.9+-14231C?style=flat-square&logo=apachemaven&logoColor=F2B544"/> <img alt="Claude" src="https://img.shields.io/badge/Claude-tool%20use-14231C?style=flat-square&logo=anthropic&logoColor=F2B544"/> <img alt="Redis" src="https://img.shields.io/badge/Redis-state-14231C?style=flat-square&logo=redis&logoColor=FF5468"/> <img alt="RabbitMQ" src="https://img.shields.io/badge/RabbitMQ-events-14231C?style=flat-square&logo=rabbitmq&logoColor=FF7A45"/>

<br/><br/>

The assistant. It talks to Claude, keeps each customer's chat and files, and prepares requests with tools.

<sub><a href="../README.md">Back to the project overview</a></sub>

<img src="../docs/assets/divider.svg" alt="" width="100%"/>

</div>

## What it does

agent-service is the only part that talks to the model. It never moves money. It can only call tools that **prepare** a request, and the phone number on every request comes from the signed-in customer's token, never from what the model typed.

| Job | Detail |
|---|---|
| Understand the customer | Text, voice notes (optional speech to text), photos, PDFs, Word, Excel, CSV and text files, shared contacts and locations |
| Keep the chat | Messages and files per phone number, with conversation memory in Redis |
| Prepare requests | Send money, Paybill, Till, Pochi la Biashara, airtime, Ratiba, and list or cancel requests, by calling companion-api |
| Hand over to a person | Escalates to the phones in `AGENT_ESCALATION_PHONES` |
| Stay safe | Refuses the PIN, treats text inside images and files as data, adds scam warnings, rate limits each customer |

> [!IMPORTANT]
> Keep this service private. Only companion-api should reach port 8082, and every call must carry the `X-Internal-Key` header.

## Package layout

```text
com.mpesaagent.agent
  AgentServiceApplication
  controller/   InboundController, ChatController            (HTTP in)
  service/      the agent loop, tools, memory, media understanding
  client/       ClaudeClient, companion and payment clients  (HTTP out)
  chat/         message and file storage
  store/        Redis backed conversation state
  config/       typed properties, internal key filter, RabbitMQ, CORS
  dto/  model/  request and response shapes, domain types
  exception/    GlobalExceptionHandler
  util/         phone and text helpers
```

## Run it

```bash
docker compose up -d redis rabbitmq
cd agent-service
cp .env.example .env            # then edit it
mvn spring-boot:run
```

The service reads `.env` through `spring.config.import: optional:file:.env[.properties]`. Real environment variables win over the file. Run the tests with `mvn clean verify`.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `INTERNAL_KEY` | required | Shared secret for calls between services |
| `ANTHROPIC_API_KEY` | required | Without it, messages are accepted but nothing replies |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | Model name. `ANTHROPIC_MAX_TOKENS` defaults to 1024 |
| `COMPANION_BASE_URL` | `http://localhost:8084` | Private address of companion-api |
| `COMPANION_PUBLIC_BASE_URL` | `http://localhost:8084` | Public address used in the links sent to customers |
| `PAYMENT_BASE_URL` | `http://localhost:8083` | Only used when business payments are on |
| `BUSINESS_NAME`, `BUSINESS_SUPPORT_PHONE` | `Our Business`, empty | Shown to customers |
| `AGENT_STORE` | `redis` | Where conversation state lives |
| `AGENT_RATE_LIMIT_PER_MINUTE` | `12` | Messages per customer per minute |
| `AGENT_ESCALATION_PHONES` | empty | Comma separated numbers that receive hand-overs |
| `AGENT_BUSINESS_PAYMENTS` | `false` | Switches the optional Daraja tools on |
| `STT_ENABLED`, `STT_BASE_URL`, `STT_API_KEY`, `STT_MODEL` | off | Voice note transcription, any OpenAI compatible endpoint |
| `TTS_ENABLED`, `TTS_BASE_URL`, `TTS_API_KEY`, `TTS_MODEL`, `TTS_VOICE` | off | Spoken replies |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | `localhost`, `6379` | Redis |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `localhost`, `5672`, `guest` | RabbitMQ |
| `CORS_ALLOWED_ORIGINS` | empty | For the browser console only. A wildcard is ignored |

## Endpoints

All are internal and need `X-Internal-Key`.

| Method and path | Purpose |
|---|---|
| `POST /internal/inbound` | Deliver a customer message straight to the agent. Answers 202 |
| `POST /internal/chat/{phone}/send` | Add an assistant message to a customer's chat |
| `GET /internal/chat/{phone}/messages` | Read a customer's chat |
| `DELETE /internal/chat/{phone}/messages` | Clear a customer's chat |
| `POST /internal/chat/{phone}/media` | Store a file in a customer's chat |
| `GET /internal/chat/{phone}/media/{id}` | Download a stored file |

The Postman folder `1. agent-service` has a ready request for each, including text, voice, image, document, contact, location, buttons and menu messages.

## Talk to it from the shell

```bash
curl -X POST http://localhost:8082/internal/inbound \
  -H "X-Internal-Key: $INTERNAL_KEY" \
  -H "Content-Type: application/json" \
  -d '{"messageId":"m1","from":"0712345678","kind":"TEXT","text":"hi"}'
```

The service answers **202 Accepted** at once and replies on its own threads. A repeated `messageId` is accepted and ignored, and **503** means that customer's previous message is still being handled. The other message kinds (`IMAGE`, `AUDIO`, `DOCUMENT`, `VIDEO`, `LOCATION`, `CONTACTS`, `BUTTON_REPLY`, `LIST_REPLY`) are in the `1. agent-service` folder of the Postman collection.

## Failing well

- `ClaudeClient` tries the model up to 3 times when the API answers 408, 429, 500, 502, 503, 504 or 529, or cannot be reached, and then raises a `ModelException` instead of hanging.
- Messages are accepted at once (202) and handled on their own threads, so a slow model never blocks the caller.
- Spring AMQP retry uses `max-retries` (renamed from `max-attempts` in Spring AMQP 4).
- 503 answers for database and broker outages are in payment-service today. Adding the same to this service's `GlobalExceptionHandler` is an open item.

## Boot 4 notes for contributors

- Jackson 3: packages are `tools.jackson.*`, annotations stay in `com.fasterxml.jackson.annotation`, and `JacksonException` is unchecked. Use `JsonMapper.builder().build()` or the `JsonMapper` bean.
- A bean with several constructors needs `@Autowired` on the one Spring should use. `ClaudeClient` has it.
- Test annotations moved to `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` and friends.

<div align="center">
<sub>M-Pesa is a registered mark of Safaricom. This project is not affiliated with or endorsed by Safaricom.</sub>
</div>
