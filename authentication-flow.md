# Authentication Activity Flow

> End-to-end authentication flow across the microservices: how a user logs in,
> how the JWT is validated by downstream services, and how the session ends on expiry.
> Colors: **blue** = action, **amber** = decision, **red** = error/terminal, **green** = success, **grey** = start/end.

## 1. Login Activity (Client + Auth Service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Start"]):::term --> L["User enters<br/>email + password"]:::act
    L --> POST["POST /auth/login"]:::act
    POST --> V["@Valid LoginRequest"]:::act
    V --> VALID{"valid?"}:::dec
    VALID -->|"invalid"| B400["400 field errors"]:::err
    VALID -->|"valid"| AUTH["AuthService.login()"]:::act
    AUTH --> F{"findByEmail<br/>in auth DB?"}:::dec
    F -->|"not found"| E401["401 Invalid email or password"]:::err
    F -->|"found"| PRO["GET /v1/users/email/{email}<br/>(service token)"]:::act
    PRO --> ACT{"profile.active?"}:::dec
    ACT -->|"inactive"| E403["403 Account not activated"]:::err
    ACT -->|"active"| MATCH{"BCrypt<br/>password matches?"}:::dec
    MATCH -->|"no"| E401
    MATCH -->|"yes"| JWT["generate JWT<br/>HS384, sub/name/role, iat/exp"]:::act
    JWT --> RES["200 LoginResponse<br/>(accessToken)"]:::ok
    RES --> STORE["auth.service.setSession()<br/>save token + user in localStorage"]:::act
    STORE --> NAV["redirect to /dashboard"]:::ok
    NAV --> Stop(["End"]):::term
    B400 --> Stop
    E401 --> Stop
    E403 --> Stop

    class VALID,F,ACT,MATCH dec;
    class L,POST,V,AUTH,PRO,JWT,STORE act;
    class B400,E401,E403 err;
    class RES,NAV ok;
    class Start,Stop term;
```

## 2. Token Validation Activity (Downstream Services)

Each service (user, product, order, log) runs the same filter. Shown for order-service.

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Protected request"]):::term --> H["JwtAuthenticationFilter<br/>doFilterInternal()"]:::act
    H --> HEADER{"Authorization header<br/>starts with 'Bearer '?"}:::dec
    HEADER -->|"no"| NOAUTH["no auth set in context"]:::act
    HEADER -->|"yes"| PARSE["extract token"]:::act
    PARSE --> VERIFY{"JwtService.parseToken()<br/>signature + exp valid?"}:::dec
    VERIFY -->|"invalid / expired"| CLEAR["clear SecurityContext"]:::act
    VERIFY -->|"valid"| CLAIMS["read sub + role"]:::act
    CLAIMS --> CTX["set authentication<br/>ROLE_&lt;role&gt; authority"]:::act
    NOAUTH --> SEC["SecurityConfig<br/>authorizeHttpRequests"]:::dec
    CLEAR --> SEC
    CTX --> SEC
    SEC -->|"endpoint requires auth"| UNAUTH{"authenticated?"}:::dec
    UNAUTH -->|"no"| U401["401 / 403"]:::err
    UNAUTH -->|"yes"| ADMIN{"ROLE_ADMIN<br/>required?"}:::dec
    ADMIN -->|"yes but not admin"| F403["403 Forbidden"]:::err
    ADMIN -->|"ok"| CTL["Controller handles request"]:::act
    CTL --> Stop(["End"]):::term
    U401 --> Stop
    F403 --> Stop

    class HEADER,VERIFY,SEC,UNAUTH,ADMIN dec;
    class H,PARSE,CLAIMS,CTX,NOAUTH,CLEAR,CTL act;
    class U401,F403 err;
    class Start,Stop term;
```

## 3. Session Expiry / Auto-Logout Activity (Frontend)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Authenticated session"]):::term --> SCH["scheduleAutoLogout()<br/>setTimeout(exp - now)"]:::act
    SCH --> TIMER{"timer fired<br/>at token expiry?"}:::dec
    TIMER -->|"yes"| LG["AuthService.logout()"]:::act
    TIMER -->|"not yet"| REQ["user navigates /<br/>makes API requests"]:::act
    REQ --> EXP{"isAuthenticated()?<br/>token expired?"}:::dec
    EXP -->|"expired"| LG
    EXP -->|"valid"| SEND["authInterceptor adds<br/>Authorization: Bearer"]:::act
    SEND --> R401{"API returns 401<br/>(invalid/expired token)?"}:::dec
    R401 -->|"yes"| LG
    R401 -->|"no"| CTX["continue app"]:::ok
    CTX --> REQ
    LG --> CLR["clear token + user<br/>from localStorage"]:::act
    CLR --> NAV["redirect to /login"]:::ok
    NAV --> Stop(["End"]):::term

    class TIMER,EXP,R401 dec;
    class SCH,REQ,SEND,LG,CLR act;
    class CTX,NAV ok;
    class Start,Stop term;
```