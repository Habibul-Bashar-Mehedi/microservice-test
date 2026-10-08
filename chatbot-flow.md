# Chatbot Activity Flow

> Role-based AI assistant for the microservice system.
> The chatbot can only perform actions allowed by the caller's role, and it can only
> act by calling the real services **with the caller's JWT** — so the backend security
> rules are the final authority.
>
> Service: **chatbot-service** (port 8085, Consul-registered).
> LLM: **OpenCode Go** (`https://opencode.ai/zen/go/v1`, OpenAI-compatible, tool calling).
> Endpoints: `POST /v1/chat`, `POST /v1/chat/confirm`, `POST /v1/chat/cancel`.
> Colors: **blue** = action, **amber** = decision, **green** = success, **red** = error,
> **grey** = start/end, **purple** = service.

## 1. Main request flow (`POST /v1/chat`)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef svc fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["User sends a message<br/>in the chat widget"]):::term
    Req["POST /v1/chat<br/>Authorization: Bearer &lt;JWT&gt;<br/>body: message, conversationId"]:::act
    Auth{"JWT valid?"}:::dec
    Role["Resolve role from JWT claim<br/>USER / MAINTAINER / MANAGER /<br/>PRODUCT_SPECIALIST / SALESMAN / ADMIN"]:::act
    Store["ConversationStore getOrCreate<br/>append user message<br/>+ role system prompt on new conversation"]:::act
    Llm["LLM call (OpenCode Go)<br/>model + role-scoped tool list<br/>+ conversation history"]:::svc
    Tools{"reply has<br/>tool_calls?"}:::dec
    NeedConf{"any requested tool<br/>requires confirmation?"}:::dec
    Pending["store pending action(s)<br/>build human-readable summary"]:::act
    ExecRO["execute read-only tools via DownstreamClient<br/>(caller JWT forwarded)"]:::act
    AppendTool["append assistant message<br/>+ tool results to history"]:::act
    Reply(["Return reply<br/>requiresConfirmation = false"]):::ok
    Ask(["Return proposal<br/>requiresConfirmation = true<br/>+ confirmationId + summary"]):::ok
    Deny(["403 Unauthorized"]):::err

    Start --> Req --> Auth
    Auth -->|"no"| Deny
    Auth -->|"yes"| Role --> Store --> Llm --> Tools
    Tools -->|"no"| Reply
    Tools -->|"yes"| NeedConf
    NeedConf -->|"yes"| Pending --> Ask
    NeedConf -->|"no"| ExecRO --> AppendTool --> Llm
```

## 2. Confirmation flow (`POST /v1/chat/confirm`)

Important actions are **never executed on the first request**. The model proposes them,
the service stores them as pending actions and returns a `confirmationId`; they execute
only after the user confirms. `/v1/chat/cancel` discards the pending actions.

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef svc fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    CStart(["User clicks Confirm<br/>POST /v1/chat/confirm<br/>conversationId + confirmationId"]):::term
    CVal{"conversation has pending actions<br/>and id matches and owner matches?"}:::dec
    CExec["for each pending action:<br/>ToolRegistry.find(role, tool)<br/>execute handler via DownstreamClient"]:::act
    CErr(["409 no pending / id mismatch<br/>403 wrong owner / role not allowed"]):::err
    CSummary["append per-action result<br/>(errors captured per action)"]:::act
    CLlm["LLM summarizes the result<br/>(deterministic fallback if LLM fails)"]:::svc
    CDone(["Return confirmation reply"]):::ok

    CStart --> CVal
    CVal -->|"no"| CErr
    CVal -->|"yes"| CExec --> CSummary --> CLlm --> CDone
```

## 3. Role enforcement (two layers)

```mermaid
flowchart LR
    classDef svc fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;

    L1["Layer 1 - chatbot-service<br/>role-scoped tool list given to the LLM<br/>+ system prompt refuses other roles"]:::dec
    L2["Layer 2 - downstream services<br/>the tool call forwards the caller's JWT<br/>and the service re-checks the role"]:::dec
    OK["Action allowed only if BOTH layers allow it"]:::ok

    L1 --> L2 --> OK
```

## 4. Downstream calls

Every tool action is an HTTP call by chatbot-service to another service, forwarding the
caller's `Authorization` header.

```mermaid
flowchart LR
    classDef svc fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    CB["chatbot-service"]:::svc
    PS["product-service<br/>products · approvals · notifications"]:::svc
    US["user-service<br/>profiles · roles"]:::svc
    OS["order-service<br/>orders · cart"]:::svc
    LS["log-service<br/>message logs"]:::svc

    CB -->|"caller JWT"| PS
    CB -->|"caller JWT"| US
    CB -->|"caller JWT"| OS
    CB -->|"caller JWT"| LS
```

## 5. Tools per role

| Role | Tools | Confirmation required |
|------|-------|-----------------------|
| General User | `list_products`, `search_products`, `get_product`, `create_order`, `list_my_orders`, `get_order`, `cancel_order`, `add_to_cart`, `view_cart`, `update_cart_quantity`, `remove_from_cart`, `clear_cart`, `checkout_cart` | `create_order`, `cancel_order`, `checkout_cart` |
| Maintainer | product read/`create_product`/`resubmit_product`, `list_orders`, `confirm_order`, `list_notifications` | `create_product`, `resubmit_product`, `confirm_order` |
| Manager / Product Specialist / Salesman | product read, `list_pending_products`, `approve_product`, `reject_product`, `list_notifications` | `approve_product`, `reject_product` |
| Admin | product read/create, `list_pending_products`, `approve_product`, `reject_product`, product updates (`update_product_name`, `update_product_price`, `add_stock`), `list_users`, `change_user_role`, `list_logs`, `list_orders`, `confirm_order` | `create_product`, `approve_product`, `reject_product`, `update_product_name`, `update_product_price`, `add_stock`, `change_user_role`, `confirm_order` |

If a role asks for an action outside its tool list, the chatbot refuses and explains that
the role does not have permission.
