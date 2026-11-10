package com.nebula.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTest {

    private static final String CART_HEADER = "X-Cart-Id";
    private static final String AURORA = "aurora-x5";
    private static final String NOVA = "nova-mini";
    private static final String CABLE = "pulse-cable";

    private static final String CUSTOMER = """
            {"name":"Даниил Тестов","email":"buyer@example.com",
             "address":"Москва, ул. Тверская, 7","comment":"Домофон 12"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("checkout creates an order with a frozen price snapshot")
    void createsOrderWithSnapshot() throws Exception {
        String cartId = newCartId();
        long productId = productId(AURORA);
        BigDecimal unitPrice = productPrice(AURORA);
        addToCart(cartId, productId, 2);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("NB-\\d{8}-[A-Z0-9]{4}")))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.statusTitle").value("Новый"))
                .andExpect(jsonPath("$.items[0].slug").value(AURORA))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.customerName").value("Даниил Тестов"))
                .andReturn();

        OrderDto order = read(result.getResponse().getContentAsString());
        assertThat(order.total()).isEqualByComparingTo(unitPrice.multiply(BigDecimal.TWO));
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/orders/" + order.code());
    }

    @Test
    @DisplayName("checkout decrements stock")
    void decrementsStock() throws Exception {
        String cartId = newCartId();
        long productId = productId(CABLE);
        int stockBefore = stockOf(CABLE);
        addToCart(cartId, productId, 2);

        mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andExpect(status().isCreated());

        assertThat(stockOf(CABLE)).isEqualTo(stockBefore - 2);
    }

    @Test
    @DisplayName("checkout empties the cart")
    void emptiesCart() throws Exception {
        String cartId = newCartId();
        addToCart(cartId, productId(NOVA), 1);

        mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andExpect(status().isCreated());

        String cart = mockMvc.perform(get("/api/cart").header(CART_HEADER, cartId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(cart).get("itemCount").asInt()).isZero();
    }

    @Test
    @DisplayName("the same cart cannot be checked out twice")
    void cannotCheckoutTwice() throws Exception {
        String cartId = newCartId();
        addToCart(cartId, productId(NOVA), 1);

        mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Корзина пуста")));
    }

    @Test
    @DisplayName("checkout of an empty cart is rejected")
    void rejectsEmptyCart() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, newCartId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("customer details are validated")
    void validatesCustomerDetails() throws Exception {
        String cartId = newCartId();
        addToCart(cartId, productId(NOVA), 1);

        mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","email":"not-an-email","address":"дом"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.address").exists());

        // the cart must survive a failed checkout
        String cart = mockMvc.perform(get("/api/cart").header(CART_HEADER, cartId))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(cart).get("itemCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("placed order can be fetched by code, case-insensitively")
    void fetchesOrderByCode() throws Exception {
        String cartId = newCartId();
        addToCart(cartId, productId(AURORA), 1);

        OrderDto created = read(mockMvc.perform(post("/api/orders")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUSTOMER))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(get("/api/orders/" + created.code()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(created.code()))
                .andExpect(jsonPath("$.items[0].slug").value(AURORA));

        mockMvc.perform(get("/api/orders/" + created.code().toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(created.code()));
    }

    @Test
    @DisplayName("unknown order code returns 404 problem detail")
    void unknownCodeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/NB-19700101-ZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Не найдено"));
    }

    // ------------------------------------------------------------------

    private void addToCart(String cartId, long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().isOk());
    }

    private long productId(String slug) throws Exception {
        return product(slug).get("id").asLong();
    }

    private BigDecimal productPrice(String slug) throws Exception {
        return new BigDecimal(product(slug).get("price").asText());
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

    private OrderDto read(String json) throws Exception {
        return objectMapper.readValue(json, OrderDto.class);
    }

    private static String newCartId() {
        return UUID.randomUUID().toString();
    }
}
