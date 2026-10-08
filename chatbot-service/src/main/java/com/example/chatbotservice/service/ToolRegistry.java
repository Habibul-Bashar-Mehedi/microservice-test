package com.example.chatbotservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * All chatbot tools and the roles allowed to use them. Tools are the only way
 * the chatbot can act, so restricting the per-role tool list is the first line
 * of role enforcement. The downstream call (with the caller's JWT) is the second.
 */
@Component
public class ToolRegistry {

    public static final String USER = "USER";
    public static final String MAINTAINER = "MAINTAINER";
    public static final String MANAGER = "MANAGER";
    public static final String PRODUCT_SPECIALIST = "PRODUCT_SPECIALIST";
    public static final String SALESMAN = "SALESMAN";
    public static final String ADMIN = "ADMIN";

    private static final Set<String> ALL_ROLES =
            Set.of(USER, MAINTAINER, MANAGER, PRODUCT_SPECIALIST, SALESMAN, ADMIN);
    private static final Set<String> STAFF = Set.of(MAINTAINER, MANAGER, PRODUCT_SPECIALIST, SALESMAN, ADMIN);
    private static final Set<String> VALID_ROLES =
            Set.of(USER, MAINTAINER, MANAGER, PRODUCT_SPECIALIST, SALESMAN, ADMIN);

    private final DownstreamClient downstream;
    private final ObjectMapper mapper;
    private final List<ToolDefinition> tools = new ArrayList<>();

    public ToolRegistry(DownstreamClient downstream, ObjectMapper mapper) {
        this.downstream = downstream;
        this.mapper = mapper;
        registerCommonTools();
        registerUserTools();
        registerMaintainerTools();
        registerReviewTools(MANAGER, "manager", "Product Specialist");
        registerReviewTools(PRODUCT_SPECIALIST, "specialist", "Salesman");
        registerReviewTools(SALESMAN, "salesman", "Admin");
        registerReviewTools(ADMIN, "admin", "final approval");
        registerAdminTools();
    }

    public List<ToolDefinition> toolsFor(String role) {
        return tools.stream().filter(tool -> tool.allows(role)).toList();
    }

    public Optional<ToolDefinition> find(String role, String name) {
        return tools.stream()
                .filter(tool -> tool.name().equals(name) && tool.allows(role))
                .findFirst();
    }

    public List<Map<String, Object>> llmSpecsFor(String role) {
        return toolsFor(role).stream().map(ToolDefinition::toLlmSpec).toList();
    }

    // ---- tool groups -------------------------------------------------------

    private void registerCommonTools() {
        tools.add(new ToolDefinition("list_products",
                "List products. General users see approved products; staff see all products with their status.",
                obj(Map.of(), List.of()), ALL_ROLES, false,
                (ctx, args) -> fetchProducts(ctx)));

        tools.add(new ToolDefinition("search_products",
                "Search products by name and return matching products.",
                obj(Map.of("query", str("Text to match against the product name")), List.of("query")),
                ALL_ROLES, false,
                (ctx, args) -> {
                    String query = args.path("query").asText("").trim().toLowerCase();
                    JsonNode all = fetchProducts(ctx);
                    if (query.isEmpty()) {
                        return all;
                    }
                    ArrayNode matches = mapper.createArrayNode();
                    for (JsonNode product : all) {
                        if (product.path("name").asText("").toLowerCase().contains(query)) {
                            matches.add(product);
                        }
                    }
                    return matches;
                }));

        tools.add(new ToolDefinition("get_product",
                "Get full details for a single product by its id.",
                obj(Map.of("id", integer("Product id")), List.of("id")),
                ALL_ROLES, false,
                (ctx, args) -> downstream.get(
                        downstream.productBase, "/v1/products/" + args.path("id").asLong(), ctx.authHeader())));

        tools.add(new ToolDefinition("list_notifications",
                "List the product approval messages/notifications for the current user.",
                obj(Map.of(), List.of()), STAFF, false,
                (ctx, args) -> downstream.get(
                        downstream.productBase, "/v1/notifications", ctx.authHeader())));
    }

    private void registerUserTools() {
        tools.add(new ToolDefinition("create_order",
                "Create one order per requested product for the current user. "
                        + "Prefer passing productId (from search_products); productName may be used as a fallback. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of(
                        "items", Map.of(
                                "type", "array",
                                "description", "Products to order",
                                "items", obj(Map.of(
                                        "productId", integer("Product id (preferred)"),
                                        "productName", str("Product name (fallback if id unknown)"),
                                        "quantity", integer("Quantity to order")),
                                        List.of("quantity")))), List.of("items")),
                Set.of(USER), true,
                this::createOrders));

        tools.add(new ToolDefinition("list_my_orders",
                "List the orders placed by the current user.",
                obj(Map.of(), List.of()), Set.of(USER), false,
                (ctx, args) -> downstream.get(
                        downstream.orderBase, "/v1/orders/user/" + currentUserId(ctx), ctx.authHeader())));

        tools.add(new ToolDefinition("get_order",
                "Get details of a specific order by id.",
                obj(Map.of("id", integer("Order id")), List.of("id")),
                Set.of(USER, MAINTAINER, ADMIN), false,
                (ctx, args) -> downstream.get(
                        downstream.orderBase, "/v1/orders/" + args.path("id").asLong(), ctx.authHeader())));

        tools.add(new ToolDefinition("cancel_order",
                "Cancel one of the current user's own pending orders by id. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of("id", integer("Order id")), List.of("id")),
                Set.of(USER), true,
                (ctx, args) -> downstream.exchange(HttpMethod.POST, downstream.orderBase,
                        "/v1/orders/" + args.path("id").asLong() + "/cancel?userId=" + currentUserId(ctx),
                        null, ctx.authHeader())));

        Map<String, Object> cartItemsParam = Map.of(
                "type", "array",
                "description", "Cart items",
                "items", obj(Map.of(
                        "productId", integer("Product id (preferred)"),
                        "productName", str("Product name (fallback if id unknown)"),
                        "quantity", integer("Quantity")),
                        List.of("quantity")));

        tools.add(new ToolDefinition("add_to_cart",
                "Add one or more products to the current user's cart. "
                        + "Prefer passing productId (from search_products); productName may be used as a fallback.",
                obj(Map.of("items", cartItemsParam), List.of("items")),
                Set.of(USER), false,
                this::addToCart));

        tools.add(new ToolDefinition("view_cart",
                "View the current user's cart with all its items and totals.",
                obj(Map.of(), List.of()), Set.of(USER), false,
                (ctx, args) -> downstream.get(
                        downstream.orderBase, "/v1/cart?userId=" + currentUserId(ctx), ctx.authHeader())));

        tools.add(new ToolDefinition("update_cart_quantity",
                "Change the quantity of a product already in the current user's cart.",
                obj(Map.of(
                        "productId", integer("Product id (preferred)"),
                        "productName", str("Product name (fallback if id unknown)"),
                        "quantity", integer("New quantity (>= 1)")), List.of("quantity")),
                Set.of(USER), false,
                this::updateCartQuantity));

        tools.add(new ToolDefinition("remove_from_cart",
                "Remove one or more products from the current user's cart.",
                obj(Map.of("items", Map.of(
                        "type", "array",
                        "description", "Cart items to remove",
                        "items", obj(Map.of(
                                "productId", integer("Product id (preferred)"),
                                "productName", str("Product name (fallback if id unknown)")),
                                List.of()))), List.of("items")),
                Set.of(USER), false,
                this::removeFromCart));

        tools.add(new ToolDefinition("clear_cart",
                "Remove all items from the current user's cart.",
                obj(Map.of(), List.of()), Set.of(USER), false,
                (ctx, args) -> {
                    downstream.exchange(HttpMethod.DELETE, downstream.orderBase,
                            "/v1/cart?userId=" + currentUserId(ctx), null, ctx.authHeader());
                    return mapper.createObjectNode().put("cleared", true);
                }));

        tools.add(new ToolDefinition("checkout_cart",
                "Create orders for all items currently in the cart and empty the cart. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of(), List.of()), Set.of(USER), true,
                (ctx, args) -> downstream.exchange(HttpMethod.POST, downstream.orderBase,
                        "/v1/cart/checkout?userId=" + currentUserId(ctx), null, ctx.authHeader())));
    }

    private void registerMaintainerTools() {
        tools.add(new ToolDefinition("create_product",
                "Create a new product. It will enter the approval flow at the Manager stage. "
                        + "This action requires confirmation before it is executed.",
                productSchema(), Set.of(MAINTAINER, ADMIN), true,
                (ctx, args) -> downstream.exchange(
                        HttpMethod.POST, downstream.productBase, "/v1/products", body(args), ctx.authHeader())));

        tools.add(new ToolDefinition("resubmit_product",
                "Correct and resubmit a rejected product, sending it back to the reviewer who rejected it. "
                        + "This action requires confirmation before it is executed.",
                withId(productSchema()), Set.of(MAINTAINER), true,
                (ctx, args) -> downstream.exchange(
                        HttpMethod.PUT, downstream.productBase,
                        "/v1/products/" + args.path("id").asLong(), body(args), ctx.authHeader())));

        tools.add(new ToolDefinition("list_orders",
                "List all orders.",
                obj(Map.of(), List.of()), Set.of(MAINTAINER, ADMIN), false,
                (ctx, args) -> downstream.get(downstream.orderBase, "/v1/orders", ctx.authHeader())));

        tools.add(new ToolDefinition("confirm_order",
                "Confirm a pending order so its stock is updated. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of("id", integer("Order id")), List.of("id")),
                Set.of(MAINTAINER, ADMIN), true,
                (ctx, args) -> downstream.exchange(
                        HttpMethod.POST, downstream.orderBase,
                        "/v1/orders/" + args.path("id").asLong() + "/confirm", null, ctx.authHeader())));
    }

    private void registerReviewTools(String role, String stage, String nextStage) {
        tools.add(new ToolDefinition("list_pending_products",
                "List products waiting for your review (" + role + " stage).",
                obj(Map.of(), List.of()), Set.of(role), false,
                (ctx, args) -> downstream.get(
                        downstream.productBase, "/v1/products/pending/" + stage, ctx.authHeader())));

        tools.add(new ToolDefinition("approve_product",
                "Approve a pending product and hand it over to the " + nextStage + ". "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of("id", integer("Product id")), List.of("id")),
                Set.of(role), true,
                (ctx, args) -> review(ctx, stage, args.path("id").asLong(), true, null)));

        tools.add(new ToolDefinition("reject_product",
                "Reject a pending product with a reason. The product and reason are reported back to the "
                        + "earlier reviewers. This action requires confirmation before it is executed.",
                obj(Map.of(
                        "id", integer("Product id"),
                        "reason", str("Reason for rejection")), List.of("id", "reason")),
                Set.of(role), true,
                (ctx, args) -> review(ctx, stage, args.path("id").asLong(), false, args.path("reason").asText())));
    }

    private void registerAdminTools() {
        tools.add(new ToolDefinition("list_users",
                "List all users.",
                obj(Map.of(), List.of()), Set.of(ADMIN), false,
                (ctx, args) -> downstream.get(downstream.userBase, "/v1/users", ctx.authHeader())));

        tools.add(new ToolDefinition("change_user_role",
                "Change a user's role. Accepts userId or email. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of(
                        "userId", integer("User id (preferred)"),
                        "email", str("User email (fallback if id unknown)"),
                        "role", str("New role: USER, ADMIN, MANAGER, MAINTAINER, PRODUCT_SPECIALIST or SALESMAN")),
                        List.of("role")),
                Set.of(ADMIN), true,
                this::changeUserRole));

        tools.add(new ToolDefinition("update_product_name",
                "Update the name of an existing product. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of("id", integer("Product id"), "name", str("New product name")), List.of("id", "name")),
                Set.of(ADMIN), true,
                (ctx, args) -> downstream.exchange(HttpMethod.PUT, downstream.productBase,
                        "/v1/products/" + args.path("id").asLong() + "/name",
                        args.path("name").asText(), ctx.authHeader())));

        tools.add(new ToolDefinition("update_product_price",
                "Update the price of an existing product. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of("id", integer("Product id"), "price", num("New price (must be > 0)")),
                        List.of("id", "price")),
                Set.of(ADMIN), true,
                (ctx, args) -> downstream.exchange(HttpMethod.PUT, downstream.productBase,
                        "/v1/products/" + args.path("id").asLong() + "/price",
                        mapper.valueToTree(args.path("price").decimalValue()), ctx.authHeader())));

        tools.add(new ToolDefinition("add_stock",
                "Add stock quantity to an existing product. "
                        + "This action requires confirmation before it is executed.",
                obj(Map.of("id", integer("Product id"), "quantity", integer("Quantity to add (>= 1)")),
                        List.of("id", "quantity")),
                Set.of(ADMIN), true,
                (ctx, args) -> downstream.exchange(HttpMethod.PUT, downstream.productBase,
                        "/v1/products/" + args.path("id").asLong() + "/add-quantity",
                        mapper.valueToTree(args.path("quantity").intValue()), ctx.authHeader())));

        tools.add(new ToolDefinition("list_logs",
                "View message logs.",
                obj(Map.of(), List.of()), Set.of(ADMIN), false,
                (ctx, args) -> downstream.get(downstream.logBase, "/v1/logs", ctx.authHeader())));
    }

    // ---- handlers ----------------------------------------------------------

    private JsonNode fetchProducts(ToolContext ctx) {
        String path = USER.equals(ctx.role()) ? "/v1/products" : "/v1/products/all";
        return downstream.get(downstream.productBase, path, ctx.authHeader());
    }

    private Object review(ToolContext ctx, String stage, long id, boolean approved, String reason) {
        ObjectNode body = mapper.createObjectNode();
        body.put("approved", approved);
        if (!approved) {
            body.put("reason", reason == null ? "" : reason);
        }
        return downstream.exchange(HttpMethod.POST, downstream.productBase,
                "/v1/products/" + id + "/" + stage + "/review", body, ctx.authHeader());
    }

    private Object createOrders(ToolContext ctx, JsonNode args) {
        long userId = currentUserId(ctx);
        ArrayNode created = mapper.createArrayNode();
        JsonNode items = args.path("items");
        List<String> problems = new ArrayList<>();

        for (JsonNode item : items) {
            Long productId = item.hasNonNull("productId") ? item.path("productId").asLong() : null;
            int quantity = item.path("quantity").asInt(0);

            if (productId == null && item.hasNonNull("productName")) {
                productId = resolveProductId(ctx, item.path("productName").asText());
            }
            if (productId == null) {
                problems.add("Unknown product: " + item.path("productName").asText("(missing)"));
                continue;
            }
            if (quantity < 1) {
                problems.add("Quantity must be at least 1 for product " + productId);
                continue;
            }

            ObjectNode body = mapper.createObjectNode();
            body.put("userId", userId);
            body.put("productId", productId);
            body.put("quantity", quantity);
            created.add(downstream.exchange(
                    HttpMethod.POST, downstream.orderBase, "/v1/orders", body, ctx.authHeader()));
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("createdOrders", created);
        if (!problems.isEmpty()) {
            result.set("problems", mapper.valueToTree(problems));
        }
        return result;
    }

    private Object addToCart(ToolContext ctx, JsonNode args) {
        long userId = currentUserId(ctx);
        ArrayNode added = mapper.createArrayNode();
        List<String> problems = new ArrayList<>();

        for (JsonNode item : args.path("items")) {
            JsonNode product = lookupProduct(ctx, item);
            int quantity = item.path("quantity").asInt(0);
            if (product == null || product.isMissingNode() || product.isNull()) {
                problems.add("Unknown product: " + item.toString());
                continue;
            }
            if (quantity < 1) {
                problems.add("Quantity must be at least 1 for " + product.path("name").asText());
                continue;
            }

            ObjectNode body = mapper.createObjectNode();
            body.put("userId", userId);
            body.put("productId", product.path("id").asLong());
            body.put("name", product.path("name").asText(null));
            body.set("price", product.path("price"));
            body.put("quantity", quantity);
            body.set("availableQuantity", product.path("availableQuantity"));
            added.add(downstream.exchange(
                    HttpMethod.POST, downstream.orderBase, "/v1/cart/items", body, ctx.authHeader()));
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("cartItems", added);
        if (!problems.isEmpty()) {
            result.set("problems", mapper.valueToTree(problems));
        }
        return result;
    }

    private Object updateCartQuantity(ToolContext ctx, JsonNode args) {
        long userId = currentUserId(ctx);
        JsonNode product = lookupProduct(ctx, args);
        int quantity = args.path("quantity").asInt(0);
        if (product == null || product.isMissingNode() || product.isNull()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown product");
        }
        if (quantity < 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Quantity must be at least 1");
        }
        return downstream.exchange(HttpMethod.PUT, downstream.orderBase,
                "/v1/cart/items/" + product.path("id").asLong() + "?userId=" + userId + "&quantity=" + quantity,
                null, ctx.authHeader());
    }

    private Object removeFromCart(ToolContext ctx, JsonNode args) {
        long userId = currentUserId(ctx);
        List<String> removed = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        for (JsonNode item : args.path("items")) {
            JsonNode product = lookupProduct(ctx, item);
            Long productId = product != null && !product.isMissingNode() && !product.isNull()
                    ? product.path("id").asLong()
                    : (item.hasNonNull("productId") ? item.path("productId").asLong() : null);
            if (productId == null) {
                problems.add("Unknown product: " + item.toString());
                continue;
            }
            downstream.exchange(HttpMethod.DELETE, downstream.orderBase,
                    "/v1/cart/items/" + productId + "?userId=" + userId, null, ctx.authHeader());
            removed.add(String.valueOf(productId));
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("removedProductIds", mapper.valueToTree(removed));
        if (!problems.isEmpty()) {
            result.set("problems", mapper.valueToTree(problems));
        }
        return result;
    }

    private JsonNode lookupProduct(ToolContext ctx, JsonNode item) {
        if (item.hasNonNull("productId")) {
            return downstream.get(downstream.productBase,
                    "/v1/products/" + item.path("productId").asLong(), ctx.authHeader());
        }
        if (item.hasNonNull("productName")) {
            Long id = resolveProductId(ctx, item.path("productName").asText());
            if (id != null) {
                return downstream.get(downstream.productBase, "/v1/products/" + id, ctx.authHeader());
            }
        }
        return null;
    }

    private Long resolveProductId(ToolContext ctx, String productName) {
        JsonNode products = fetchProducts(ctx);
        String needle = productName.toLowerCase();
        for (JsonNode product : products) {
            if (product.path("name").asText("").toLowerCase().equals(needle)) {
                return product.path("id").asLong();
            }
        }
        for (JsonNode product : products) {
            if (product.path("name").asText("").toLowerCase().contains(needle)) {
                return product.path("id").asLong();
            }
        }
        return null;
    }

    private Object changeUserRole(ToolContext ctx, JsonNode args) {
        String role = args.path("role").asText("").trim().toUpperCase();
        if (!VALID_ROLES.contains(role)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid role: " + role);
        }

        Long userId = args.hasNonNull("userId") ? args.path("userId").asLong() : null;
        if (userId == null && args.hasNonNull("email")) {
            JsonNode profile = downstream.get(downstream.userBase,
                    "/v1/users/email/" + args.path("email").asText(), ctx.authHeader());
            userId = profile.path("id").asLong();
        }
        if (userId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Provide userId or email");
        }

        return downstream.exchange(HttpMethod.PATCH, downstream.userBase,
                "/v1/users/" + userId + "/role", Map.of("role", role), ctx.authHeader());
    }

    private long currentUserId(ToolContext ctx) {
        JsonNode profile = downstream.get(downstream.userBase,
                "/v1/users/email/" + ctx.email(), ctx.authHeader());
        return profile.path("id").asLong();
    }

    // ---- schema helpers ----------------------------------------------------

    private Map<String, Object> productSchema() {
        return obj(new LinkedHashMap<>(Map.of(
                "name", str("Product name"),
                "price", num("Price (must be > 0)"),
                "availableQuantity", integer("Available quantity (>= 0)"),
                "category", str("Category: OTHER, CHAL, DAL, ATA, MOYDA, CHINI or MOSHLA"))),
                List.of("name", "price", "availableQuantity"));
    }

    private ObjectNode body(JsonNode args) {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", args.path("name").asText());
        body.put("price", args.path("price").decimalValue());
        body.put("availableQuantity", args.path("availableQuantity").asInt());
        body.put("category", args.path("category").asText("OTHER"));
        return body;
    }

    private Map<String, Object> withId(Map<String, Object> schema) {
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) schema.get("properties"));
        properties.put("id", integer("Product id"));
        List<String> required = new ArrayList<>((List<String>) schema.get("required"));
        required.add("id");
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> obj(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> str(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> num(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }
}
