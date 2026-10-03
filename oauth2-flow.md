# OAuth2 Activity Flow

> OAuth2 (Google) sign-in flow for this system: how the browser obtains a Google
> id_token, how auth-service verifies it and issues the app's HS384 JWT, and how the
> role is resolved / synced to user-service.
> Colors: **blue** = action, **amber** = decision, **red** = error/terminal, **green** = success, **grey** = start/end, **purple** = external (Google).

## 1. Google Sign-In (Frontend)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef ext fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["User opens /login"]):::term --> GIS["google.accounts.id.initialize()<br/>client_id from api-config"]:::act
    GIS --> BTN["google.accounts.id.renderButton()"]:::act
    BTN --> CLICK{"user clicks<br/>'Sign in with Google'?"}:::dec
    CLICK -->|"no"| IDLE["button idle"]:::act
    IDLE --> CLICK
    CLICK -->|"yes"| REDIR["Google account chooser /<br/>consent prompt"]:::ext
    REDIR --> AUTHZ{"user authorizes<br/>app?"}:::dec
    AUTHZ -->|"denied"| DENY["login stays on page"]:::err
    AUTHZ -->|"approved"| IDTOKEN["Google returns<br/>id_token (JWT)"]:::ext
    IDTOKEN --> CB["onCredential(credential)"]:::act
    CB --> POST["POST /auth/google<br/>{idToken}"]:::act
    POST --> Stop(["Continue to<br/>auth-service"]):::term

    class CLICK,AUTHZ dec;
    class GIS,BTN,REDIR,IDTOKEN,CB,POST act;
    class REDIR,IDTOKEN ext;
    class DENY err;
    class Start,Stop term;
```

## 2. Token Verification + JWT Issuance (Auth Service)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;
    classDef ext fill:#f3e8fd,stroke:#9334e6,stroke-width:1px;

    Start(["POST /auth/google"]):::term --> V["@Valid GoogleRequest<br/>(@NotBlank idToken)"]:::act
    V --> DEC["googleJwtDecoder.decode(idToken)<br/>Google JWKS + issuer + audience"]:::ext
    DEC --> VALID{"token valid?"}:::dec
    VALID -->|"invalid"| U401["401 Invalid Google token"]:::err
    VALID -->|"valid"| EXTRACT["read email + name<br/>claims"]:::act
    EXTRACT --> NOEMAIL{"email claim<br/>present?"}:::dec
    NOEMAIL -->|"null"| U401
    NOEMAIL -->|"yes"| ROLE["resolveRole(name, email)"]:::act
    ROLE --> FOUND{"auth_users<br/>findByEmail?"}:::dec
    FOUND -->|"existing"| ROLERET["use stored role"]:::act
    FOUND -->|"new user"| SAVE["save AuthUser<br/>role = USER"]:::act
    SAVE --> ROLERET
    ROLERET --> GEN["JwtService.generateToken()<br/>HS384, sub=email, role, exp +1d"]:::act
    GEN --> SYNC["registerInUserService()<br/>POST /v1/users/register"]:::act
    SYNC --> REG{"user already<br/>registered?"}:::dec
    REG -->|"yes (409)"| FETCH["GET /v1/users/email/{email}<br/>fetch profile"]:::act
    REG -->|"no"| CREATE["user-service creates profile<br/>(role synced, active=true)"]:::ok
    FETCH --> ACTIVE
    CREATE --> ACTIVE{"profile.active?<br/>(from user-service)"}:::dec
    ACTIVE -->|"inactive"| E403["403 Account is inactive.<br/>Contact administrator"]:::err
    ACTIVE -->|"active"| RESP["200 LoginResponse<br/>accessToken, tokenType, email,<br/>name, role"]:::ok
    RESP --> STOP(["frontend setSession()<br/>+ redirect"]):::term
    E403 --> STOP

    class VALID,NOEMAIL,FOUND,REG,ACTIVE dec;
    class V,DEC,EXTRACT,ROLE,ROLERET,SAVE,GEN,SYNC,FETCH act;
    class U401,E403 err;
    class RESP,CREATE ok;
    class Start,STOP term;
```

## 3. Downstream Validation (All Services)

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef err fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["API request<br/>with Bearer token"]):::term --> H{"Authorization header<br/>starts with 'Bearer '?"}:::dec
    H -->|"no"| NOAUTH["no auth context"]:::act
    H -->|"yes"| PARSE["extract JWT"]:::act
    PARSE --> VERIFY{"HS384 decode with<br/>shared jwt.secret + exp?"}:::dec
    VERIFY -->|"invalid / expired"| CLEAR["clear SecurityContext"]:::act
    VERIFY -->|"valid"| ROLE["read sub + role claim"]:::act
    ROLE --> SET["set ROLE_&lt;role&gt;<br/>authority"]:::act
    NOAUTH --> SEC["authorizeHttpRequests"]:::dec
    CLEAR --> SEC
    SET --> SEC
    SEC -->|"role not met"| D403["401 / 403"]:::err
    SEC -->|"allowed"| CTL["controller runs"]:::ok
    CTL --> Stop(["End"]):::term
    D403 --> Stop

    class H,VERIFY,SEC dec;
    class PARSE,ROLE,SET,NOAUTH,CLEAR act;
    class D403 err;
    class CTL ok;
    class Start,Stop term;
```