package com.nebula.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.store.web.dto.CartDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartApiTest {

    private static final String CART_HEADER = "X-Cart-Id";
    private static final String AURORA = "aurora-x5";
    private static final String NOVA = "nova-mini";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("a fresh cart is empty and gets a server-generated id")
    void freshCartIsEmpty() throws Exception {
        CartDto cart = read(mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn().getResponse().getContentAsString());

        assertThat(cart.id()).isNotBlank();
        assertThat(cart.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("adding a product stores quantity and computes totals from the catalog price")
    void addsProductAndComputesTotals() throws Exception {
        String cartId = newCartId();
        long productId = productId(AURORA);

        CartDto cart = add(cartId, productId, 2);

        assertThat(cart.itemCount()).isEqualTo(2);
        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).slug()).isEqualTo(AURORA);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
        assertThat(cart.subtotal())
                .isEqualByComparingTo(cart.items().get(0).unitPrice().multiply(BigDecimal.TWO));
    }

    @Test
    @DisplayName("adding the same product twice increments the existing line")
    void addingSameProductIncrementsLine() throws Exception {
        String cartId = newCartId();
        long productId = productId(AURORA);

        add(cartId, productId, 1);
        CartDto cart = add(cartId, productId, 2);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(3);
        assertThat(cart.itemCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("quantity cannot exceed available stock")
    void rejectsQuantityAboveStock() throws Exception {
        String cartId = newCartId();
        long productId = productId(AURORA);

        mockMvc.perform(post("/api/cart/items")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 50)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("в наличии")));
    }

    @Test
    @DisplayName("quantity zero is rejected on add but removes the line on update")
    void zeroQuantityRemovesLine() throws Exception {
        String cartId = newCartId();
        long productId = productId(NOVA);

        mockMvc.perform(post("/api/cart/items")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        add(cartId, productId, 2);

        CartDto cart = read(mockMvc.perform(put("/api/cart/items/" + productId)
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(cart.items()).isEmpty();
        assertThat(cart.itemCount()).isZero();
    }

    @Test
    @DisplayName("PUT sets an absolute quantity")
    void updatesAbsoluteQuantity() throws Exception {
        String cartId = newCartId();
        long productId = productId(NOVA);

        add(cartId, productId, 1);
        CartDto cart = read(mockMvc.perform(put("/api/cart/items/" + productId)
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(4);
        assertThat(cart.itemCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("lines can be removed individually and the whole cart can be cleared")
    void removesLinesAndClearsCart() throws Exception {
        String cartId = newCartId();
        long aurora = productId(AURORA);
        long nova = productId(NOVA);

        add(cartId, aurora, 1);
        add(cartId, nova, 1);

        CartDto afterRemove = read(mockMvc.perform(delete("/api/cart/items/" + aurora)
                        .header(CART_HEADER, cartId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(afterRemove.items()).hasSize(1);
        assertThat(afterRemove.items().get(0).productId()).isEqualTo(nova);

        CartDto afterClear = read(mockMvc.perform(delete("/api/cart").header(CART_HEADER, cartId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(afterClear.items()).isEmpty();
        assertThat(afterClear.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("carts are isolated by cart id")
    void cartsAreIsolated() throws Exception {
        String first = newCartId();
        String second = newCartId();
        long productId = productId(AURORA);

        add(first, productId, 2);

        CartDto other = read(mockMvc.perform(get("/api/cart").header(CART_HEADER, second))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(other.itemCount()).isZero();
        assertThat(other.id()).isEqualTo(second);
    }

    @Test
    @DisplayName("adding an unknown product returns 404")
    void unknownProductReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header(CART_HEADER, newCartId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999_999L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Не найдено"));
    }

    // ------------------------------------------------------------------

    private CartDto add(String cartId, long productId, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/cart/items")
                        .header(CART_HEADER, cartId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(productId, quantity)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return read(response);
    }

    private long productId(String slug) throws Exception {
        JsonNode catalog = objectMapper.readTree(mockMvc.perform(get("/api/products"))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode product : catalog) {
            if (slug.equals(product.get("slug").asText())) {
                return product.get("id").asLong();
            }
        }
        throw new IllegalStateException("Product not seeded: " + slug);
    }

    private CartDto read(String json) throws Exception {
        return objectMapper.readValue(json, CartDto.class);
    }

    private static String body(long productId, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity);
    }

    private static String newCartId() {
        return UUID.randomUUID().toString();
    }
}
