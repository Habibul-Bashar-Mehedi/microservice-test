package com.example.chatbotservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(mock(DownstreamClient.class), new ObjectMapper());
    }

    private List<String> names(String role) {
        return registry.toolsFor(role).stream().map(ToolDefinition::name).toList();
    }

    private Map<String, ToolDefinition> byName(String role) {
        return registry.toolsFor(role).stream()
                .collect(Collectors.toMap(ToolDefinition::name, Function.identity()));
    }

    @Test
    void generalUserTools() {
        assertThat(names("USER"))
                .contains("list_products", "search_products", "get_product", "create_order",
                        "list_my_orders", "get_order", "cancel_order",
                        "add_to_cart", "view_cart", "update_cart_quantity", "remove_from_cart",
                        "clear_cart", "checkout_cart")
                .doesNotContain("create_product", "approve_product", "list_users", "list_logs");
        assertThat(byName("USER").get("create_order").requiresConfirmation()).isTrue();
        assertThat(byName("USER").get("checkout_cart").requiresConfirmation()).isTrue();
        assertThat(byName("USER").get("cancel_order").requiresConfirmation()).isTrue();
        assertThat(byName("USER").get("add_to_cart").requiresConfirmation()).isFalse();
        assertThat(byName("USER").get("search_products").requiresConfirmation()).isFalse();
    }

    @Test
    void maintainerTools() {
        assertThat(names("MAINTAINER"))
                .contains("create_product", "resubmit_product", "list_orders", "confirm_order",
                        "list_notifications", "list_products")
                .doesNotContain("approve_product", "list_users", "create_order");
        assertThat(byName("MAINTAINER").get("create_product").requiresConfirmation()).isTrue();
        assertThat(byName("MAINTAINER").get("confirm_order").requiresConfirmation()).isTrue();
    }

    @Test
    void managerTools() {
        assertThat(names("MANAGER"))
                .contains("list_pending_products", "approve_product", "reject_product", "list_notifications")
                .doesNotContain("create_product", "create_order", "list_users", "confirm_order");
        assertThat(byName("MANAGER").get("approve_product").requiresConfirmation()).isTrue();
        assertThat(byName("MANAGER").get("reject_product").requiresConfirmation()).isTrue();
    }

    @Test
    void specialistAndSalesmanHaveTheirOwnReviewTools() {
        assertThat(names("PRODUCT_SPECIALIST"))
                .contains("list_pending_products", "approve_product", "reject_product")
                .doesNotContain("list_users");
        assertThat(names("SALESMAN"))
                .contains("list_pending_products", "approve_product", "reject_product")
                .doesNotContain("list_users", "create_product");
    }

    @Test
    void adminHasBroadestTools() {
        assertThat(names("ADMIN"))
                .contains("create_product", "approve_product", "reject_product", "list_users",
                        "change_user_role", "list_logs", "list_orders", "confirm_order", "list_products",
                        "update_product_name", "update_product_price", "add_stock")
                .doesNotContain("create_order");
        assertThat(byName("ADMIN").get("change_user_role").requiresConfirmation()).isTrue();
        assertThat(byName("ADMIN").get("update_product_name").requiresConfirmation()).isTrue();
        assertThat(byName("ADMIN").get("update_product_price").requiresConfirmation()).isTrue();
        assertThat(byName("ADMIN").get("add_stock").requiresConfirmation()).isTrue();
        assertThat(byName("ADMIN").get("list_logs").requiresConfirmation()).isFalse();
    }

    @Test
    void find_isRoleScoped() {
        assertThat(registry.find("USER", "create_product")).isEmpty();
        assertThat(registry.find("MAINTAINER", "create_product")).isPresent();
        assertThat(registry.find("MANAGER", "approve_product")).isPresent();
        assertThat(registry.find("USER", "approve_product")).isEmpty();
    }
}
