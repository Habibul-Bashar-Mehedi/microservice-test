# Product Approval Activity Flow

> End-to-end review and rejection flow for products in **product-service**.
> A product must pass every stage in order:
> **Maintainer → Manager → Product Specialist → Salesman → Admin**.
> Every rejection sends a rejection report (reason) to the earlier reviewers, and the
> Maintainer corrects the product and resubmits it to the stage that rejected it.
>
> Statuses: `PENDING_MANAGER`, `PENDING_PRODUCT_SPECIALIST`, `PENDING_SALESMAN`,
> `PENDING_ADMIN`, `APPROVED`, `REJECTED_BY_MANAGER`, `REJECTED_BY_PRODUCT_SPECIALIST`,
> `REJECTED_BY_SALESMAN`, `REJECTED_BY_ADMIN`.
>
> Colors: **blue** = action, **amber** = decision, **red** = rejection, **green** = success,
> **grey** = start/end.

## 1. Activity Flow

```mermaid
flowchart TD
    classDef act fill:#e8f0fe,stroke:#1a73e8,stroke-width:1px;
    classDef dec fill:#fef7e0,stroke:#f9ab00,stroke-width:1px;
    classDef rej fill:#fce8e6,stroke:#d93025,stroke-width:1px;
    classDef ok fill:#e6f4ea,stroke:#188038,stroke-width:1px;
    classDef term fill:#f1f3f4,stroke:#5f6368,stroke-width:1px;

    Start(["Start"]):::term
    Create["Maintainer creates product<br/>POST /v1/products"]:::act
    PM["status = PENDING_MANAGER"]:::act
    Start --> Create --> PM

    Mgr{"Manager review<br/>POST /v1/products/:id/manager/review"}:::dec
    PM --> Mgr
    Mgr -->|"approve"| PPS["status = PENDING_PRODUCT_SPECIALIST<br/>notify role PRODUCT_SPECIALIST"]:::act
    Mgr -->|"reject + reason"| RM["status = REJECTED_BY_MANAGER<br/>rejection report → Maintainer"]:::rej
    RM --> F1["Maintainer corrects &amp; resubmits<br/>PUT /v1/products/:id"]:::act
    F1 --> PM

    Spec{"Product Specialist review<br/>POST /v1/products/:id/specialist/review"}:::dec
    PPS --> Spec
    Spec -->|"approve"| PSM["status = PENDING_SALESMAN<br/>notify role SALESMAN"]:::act
    Spec -->|"reject + reason"| RPS["status = REJECTED_BY_PRODUCT_SPECIALIST<br/>rejection report → Manager, Maintainer"]:::rej
    RPS --> F2["Maintainer corrects &amp; resubmits"]:::act
    F2 --> PPS

    Sales{"Salesman review<br/>POST /v1/products/:id/salesman/review"}:::dec
    PSM --> Sales
    Sales -->|"approve"| PAD["status = PENDING_ADMIN<br/>notify role ADMIN"]:::act
    Sales -->|"reject + reason"| RSL["status = REJECTED_BY_SALESMAN<br/>rejection report → Specialist, Manager, Maintainer"]:::rej
    RSL --> F3["Maintainer corrects &amp; resubmits"]:::act
    F3 --> PSM

    Adm{"Admin final review<br/>POST /v1/products/:id/admin/review"}:::dec
    PAD --> Adm
    Adm -->|"approve"| Done["status = APPROVED<br/>notify Maintainer, Manager, Specialist, Salesman"]:::ok
    Adm -->|"reject + reason"| RAD["status = REJECTED_BY_ADMIN<br/>rejection report → Salesman, Specialist, Manager, Maintainer"]:::rej
    RAD --> F4["Maintainer corrects &amp; resubmits"]:::act
    F4 --> PAD

    Done --> Stop(["End"]):::term
```

## 2. Stage Responsibilities

| # | Stage | Role | Action on approve | Action on reject |
|---|-------|------|-------------------|------------------|
| 1 | Create | Maintainer | Product enters `PENDING_MANAGER` | — |
| 2 | Review | Manager | → `PENDING_PRODUCT_SPECIALIST` | → `REJECTED_BY_MANAGER`, report to Maintainer |
| 3 | Review | Product Specialist | → `PENDING_SALESMAN` | → `REJECTED_BY_PRODUCT_SPECIALIST`, report to Manager, Maintainer |
| 4 | Review | Salesman | → `PENDING_ADMIN` | → `REJECTED_BY_SALESMAN`, report to Specialist, Manager, Maintainer |
| 5 | Final review | Admin | → `APPROVED` (final) | → `REJECTED_BY_ADMIN`, report to Salesman, Specialist, Manager, Maintainer |

## 3. Rejection Report Matrix

| Rejected by | Rejection status | Report recipients |
|-------------|------------------|-------------------|
| Manager | `REJECTED_BY_MANAGER` | Maintainer |
| Product Specialist | `REJECTED_BY_PRODUCT_SPECIALIST` | Manager, Maintainer |
| Salesman | `REJECTED_BY_SALESMAN` | Product Specialist, Manager, Maintainer |
| Admin | `REJECTED_BY_ADMIN` | Salesman, Product Specialist, Manager, Maintainer |

## 4. Resubmission Rule

After any rejection the Maintainer edits the product and calls `PUT /v1/products/{id}`.
The product returns to the **same stage that rejected it**:

| Rejection status | Resubmits to |
|------------------|--------------|
| `REJECTED_BY_MANAGER` | `PENDING_MANAGER` |
| `REJECTED_BY_PRODUCT_SPECIALIST` | `PENDING_PRODUCT_SPECIALIST` |
| `REJECTED_BY_SALESMAN` | `PENDING_SALESMAN` |
| `REJECTED_BY_ADMIN` | `PENDING_ADMIN` |

Only the Maintainer who created the product may resubmit it, and only rejected
products can be resubmitted.

## 5. Endpoints

| Endpoint | Method | Role |
|----------|--------|------|
| `/v1/products` | POST | Maintainer |
| `/v1/products/{id}` | PUT | Maintainer (resubmit) |
| `/v1/products/{id}/manager/review` | POST | Manager |
| `/v1/products/{id}/specialist/review` | POST | Product Specialist |
| `/v1/products/{id}/salesman/review` | POST | Salesman |
| `/v1/products/{id}/admin/review` | POST | Admin |
