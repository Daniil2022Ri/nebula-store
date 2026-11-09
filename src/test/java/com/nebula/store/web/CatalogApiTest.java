package com.nebula.store.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiTest {

    private static final int SEEDED_PRODUCTS = 12;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/products returns the seeded catalog")
    void returnsSeededCatalog() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(SEEDED_PRODUCTS)))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].inStock").isBoolean());
    }

    @Test
    @DisplayName("category filter narrows the catalog to a single category")
    void filtersByCategory() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "Аудио"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].category", everyItem(is("Аудио"))));
    }

    @Test
    @DisplayName("search is case-insensitive and matches the product name")
    void searchesCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "НАУШНИКИ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("aurora-x5"));
    }

    @Test
    @DisplayName("search returns an empty array when nothing matches")
    void returnsEmptyArrayForUnknownSearch() throws Exception {
        mockMvc.perform(get("/api/products").param("q", "телепорт"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("sort=price_asc orders products from cheapest to most expensive")
    void sortsByPriceAscending() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products").param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andReturn();

        List<BigDecimal> prices = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .findValuesAsText("price")
                .stream()
                .map(BigDecimal::new)
                .toList();

        assertThat(prices).hasSize(SEEDED_PRODUCTS).isSorted();
    }

    @Test
    @DisplayName("categories endpoint exposes names with product counts")
    void exposesCategoriesWithCounts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andReturn();

        JsonNode categories = objectMapper.readTree(result.getResponse().getContentAsString());
        int total = 0;
        for (JsonNode category : categories) {
            assertThat(category.get("name").asText()).isNotBlank();
            total += category.get("count").asInt();
        }
        assertThat(total).isEqualTo(SEEDED_PRODUCTS);
    }

    @Test
    @DisplayName("missing product is reported as an RFC 7807 problem detail")
    void returnsProblemDetailForMissingProduct() throws Exception {
        mockMvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Не найдено"))
                .andExpect(jsonPath("$.detail").value("Товар с id=999999 не найден"));
    }

    @Test
    @DisplayName("non-numeric product id is rejected with 400")
    void rejectsMalformedProductId() throws Exception {
        mockMvc.perform(get("/api/products/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
