package com.nebula.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.store.domain.OrderStatus;
import com.nebula.store.web.dto.AdminStats;
import com.nebula.store.web.dto.OrderDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiTest {

    private static final String CART_HEADER = "X-Cart-Id";
    private static final String ADMIN_HEADER = "X-Admin-Token";
    private static final String TOKEN = "test-admin-token";
    private static final String CABLE = "pulse-cable";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("admin endpoints reject requests without a token")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Требуется токен администратора"));
    }

    @Test
    @DisplayName("admin endpoints reject a wrong token")
    void rejectsWrongToken() throws Exception {
        mockMvc.perform(get("/api/admin/orders").header(ADMIN_HEADER, "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("orders are listed newest first with paging metadata")
    void listsOrdersNewestFirst() throws Exception {
        String first = placeOrder(CABLE, 1);
        String second = placeOrder(CABLE, 1);

        MvcResult result = mockMvc.perform(get("/api/admin/orders").param("size", "50").header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.size").value(50))
                .andReturn();

        JsonNode page = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);

        String newest = page.get("items").get(0).get("code").asText();
        assertThat(newest).isEqualTo(second);
        assertThat(page.get("items").findValuesAsText("code")).contains(first, second);
    }

    @Test
    @DisplayName("orders can be filtered by status")
    void filtersByStatus() throws Exception {
        String code = placeOrder(CABLE, 1);
        moveTo(code, "PAID");

        MvcResult result = mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PAID")
                        .param("size", "50")
                        .header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        assertThat(items.findValuesAsText("code")).contains(code);
        items.forEach(item -> assertThat(item.get("status").asText()).isEqualTo("PAID"));
    }

    @Test
    @DisplayName("status moves through the whole lifecycle")
    void movesThroughLifecycle() throws Exception {
        String code = placeOrder(CABLE, 1);

        assertThat(moveTo(code, "PAID").status()).isEqualTo(OrderStatus.PAID);
        assertThat(moveTo(code, "SHIPPED").status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(moveTo(code, "DELIVERED").status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("illegal status jumps are rejected")
    void rejectsIllegalTransition() throws Exception {
        String code = placeOrder(CABLE, 1);

        mockMvc.perform(patch("/api/admin/orders/{code}/status", code)
                        .header(ADMIN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("нельзя перейти")));
    }

    @Test
    @DisplayName("cancelling an order returns its units to stock")
    void cancellingRestoresStock() throws Exception {
        int before = stockOf(CABLE);
        String code = placeOrder(CABLE, 2);
        assertThat(stockOf(CABLE)).isEqualTo(before - 2);

        assertThat(moveTo(code, "CANCELLED").status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(CABLE)).isEqualTo(before);
    }

    @Test
    @DisplayName("a delivered order can no longer be cancelled")
    void deliveredOrderIsFinal() throws Exception {
        String code = placeOrder(CABLE, 1);
        moveTo(code, "PAID");
        moveTo(code, "SHIPPED");
        moveTo(code, "DELIVERED");

        mockMvc.perform(patch("/api/admin/orders/{code}/status", code)
                        .header(ADMIN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("stats aggregate non-cancelled orders and count every status")
    void statsAggregateOrders() throws Exception {
        AdminStats before = readStats();
        String code = placeOrder(CABLE, 1);
        OrderDto order = fetchOrder(code);

        AdminStats after = readStats();
        assertThat(after.totalOrders()).isEqualTo(before.totalOrders() + 1);
        assertThat(after.revenue()).isEqualByComparingTo(before.revenue().add(order.total()));
        assertThat(after.byStatus()).containsKeys("NEW", "PAID", "SHIPPED", "DELIVERED", "CANCELLED");
        assertThat(after.byStatus().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(after.totalOrders());
    }

    @Test
    @DisplayName("cancelled orders are excluded from revenue")
    void cancelledOrdersDoNotCountInRevenue() throws Exception {
        String code = placeOrder(CABLE, 1);
        AdminStats afterOrder = readStats();
        moveTo(code, "CANCELLED");
        AdminStats afterCancel = readStats();

        OrderDto order = fetchOrder(code);
        assertThat(afterCancel.revenue()).isEqualByComparingTo(afterOrder.revenue().subtract(order.total()));
    }

    @Test
    @DisplayName("unknown order code returns 404")
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/admin/orders/{code}", "NB-19700101-ZZZZ").header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Не найдено"));
    }

    @Test
    @DisplayName("page size is clamped and invalid status is rejected")
    void validatesQueryParameters() throws Exception {
        mockMvc.perform(get("/api/admin/orders").param("size", "999").header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));

        mockMvc.perform(get("/api/admin/orders").param("status", "NOT_A_STATUS").header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------

    private OrderDto moveTo(String code, String status) throws Exception {
        String body = mockMvc.perform(patch("/api/admin/orders/{code}/status", code)
                        .header(ADMIN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, OrderDto.class);
    }

    private String placeOrder(String slug, int quantity) throws Exception {
        String cartId = UUID.randomUUID().toString();
        long productId = product(slug).get("id").asLong();

        mockMvc.perform(post("/api/cart/items")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Админ Тест","email":"admin-test@example.com",
                                 "address":"Москва, ул. Тестовая, 1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("code").asText();
    }

    private OrderDto fetchOrder(String code) throws Exception {
        String body = mockMvc.perform(get("/api/admin/orders/{code}", code).header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, OrderDto.class);
    }

    private AdminStats readStats() throws Exception {
        String body = mockMvc.perform(get("/api/admin/stats").header(ADMIN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AdminStats.class);
    }

    private int stockOf(String slug) throws Exception {
        return product(slug).get("stock").asInt();
    }

    private JsonNode product(String slug) throws Exception {
        JsonNode catalog = objectMapper.readTree(mockMvc.perform(get("/api/products"))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode product : catalog) {
            if (slug.equals(product.get("slug").asText())) {
                return product;
            }
        }
        throw new IllegalStateException("Product not seeded: " + slug);
    }
}
