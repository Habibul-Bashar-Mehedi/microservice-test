# Auth Service Activity Flow

> Activity diagrams for the auth-service. Colors mark the node roles: **blue** = action,
> **amber** = decision, **red** = error/terminal response, **green** = success response,
> **grey** = start/end.

## Login Activity (`POST /auth/login`)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Start"]):::term --> V["@Valid LoginRequest<br/>(email, password)"]:::act
    V --> VALID{"valid?"}:::dec
    VALID -->|"invalid"| B400["400 field errors"]:::err
    VALID -->|"valid"| L["AuthService.login()"]:::act
    L --> F{"findByEmail(email)<br/>in auth DB?"}:::dec
    F -->|"not found"| E401["401 Invalid email or password"]:::err
    F -->|"found"| GETP["GET /v1/users/email/{email}<br/>(service token)"]:::act
    GETP --> AC{"profile.active?"}:::dec
    AC -->|"inactive"| E403["403 Account is not activated yet"]:::err
    AC -->|"active"| MATCH{"BCrypt<br/>password matches?"}:::dec
    MATCH -->|"no"| E401
    MATCH -->|"yes"| JWT["generate JWT<br/>HS384, sub/name/role, iat, exp"]:::act
    JWT --> RESP["200 LoginResponse<br/>accessToken, Bearer, user info"]:::ok
    RESP --> Stop(["End"]):::term
    E401 --> Stop
    E403 --> Stop
    B400 --> Stop

    class VALID,F,AC,MATCH dec;
    class V,L,GETP,JWT act;
    class E401,E403,B400 err;
    class RESP ok;
    class Start,Stop term;
```

## Register Activity (`POST /auth/register`)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Start"]):::term --> V["@Valid RegisterRequest<br/>(name, email, password)"]:::act
    V --> VALID{"valid?"}:::dec
    VALID -->|"invalid"| B400["400 field errors"]:::err
    VALID -->|"valid"| REG["AuthService.register()"]:::act
    REG --> DUP{"email already exists<br/>in auth DB?"}:::dec
    DUP -->|"yes"| C409["409 User with email already exists"]:::err
    DUP -->|"no"| USP["POST /v1/users/register<br/>(service token)"]:::act
    USP --> SAVE["save AuthUser<br/>(role USER, BCrypt password)"]:::act
    SAVE --> RESP["200 LoginResponse<br/>(accessToken = null)"]:::ok
    RESP --> Stop(["End"]):::term
    B400 --> Stop
    C409 --> Stop

    class VALID,DUP dec;
    class V,REG,USP,SAVE act;
    class B400,C409 err;
    class RESP ok;
    class Start,Stop term;
```

## Startup Activity (DataSeeder)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["App start"]):::term --> RUN["CommandLineRunner: DataSeeder"]:::act
    RUN --> EX{"admin@example.com<br/>exists?"}:::dec
    EX -->|"no"| SEED["save AuthUser<br/>Admin / admin123 / ADMIN"]:::act
    SEED --> Stop(["End"]):::term
    EX -->|"yes"| SKIP["no-op"]:::act
    SKIP --> Stop
```

## JWT & Inter-Service Access

```mermaid
flowchart LR
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    LOGIN(["User logs in"]):::term --> JWT["JWT (HS384)<br/>sub = email, name, role,<br/>iat, exp"]:::act
    JWT --> USR["Other services validate<br/>JWT via JwtAuthenticationFilter"]:::act
    JWT --> SRV["auth-service issues a<br/>service token (role SERVICE)<br/>for user-service calls"]:::act
    SRV --> UP["user-service endpoints"]:::ok
    USR --> API["Protected endpoints"]:::ok

    class JWT,USR,SRV act;
    class UP,API ok;
    class LOGIN term;
```