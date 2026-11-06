package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProductDetailApiTests extends CatalogTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void returnsProductWithCategoryAndVariantsSortedByPrice() throws Exception {
        long id = product("stapovi", "Detail Rod", "Acme", true);
        jdbc.update("UPDATE products SET description = ? WHERE id = ?", "Opis štapa.", id);
        variantWithAttributes(id, "D-270", "{\"length\":\"2.7m\"}", "4900.00", true, 2);
        variantWithAttributes(id, "D-240", "{\"length\":\"2.4m\"}", "4500.00", true, 0);

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Detail Rod"))
                .andExpect(jsonPath("$.brand").value("Acme"))
                .andExpect(jsonPath("$.description").value("Opis štapa."))
                .andExpect(jsonPath("$.category.name").value("Štapovi"))
                .andExpect(jsonPath("$.category.slug").value("stapovi"))
                .andExpect(jsonPath("$.variants", hasSize(2)))
                .andExpect(jsonPath("$.variants[*].sku", contains("D-240", "D-270")))
                .andExpect(jsonPath("$.variants[0].attributes.length").value("2.4m"))
                .andExpect(jsonPath("$.variants[0].price").value(4500.0))
                .andExpect(jsonPath("$.variants[0].inStock").value(false))
                .andExpect(jsonPath("$.variants[1].inStock").value(true));
    }

    @Test
    void doesNotExposeExactStockQuantities() throws Exception {
        long id = product("stapovi", "Quantity Rod", "Acme", true);
        variant(id, "Q-1", "100.00", true, 7);

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].quantity").doesNotExist())
                .andExpect(jsonPath("$.variants[0].inStock").value(true));
    }

    @Test
    void hidesInactiveVariants() throws Exception {
        long id = product("stapovi", "Variant Rod", "Acme", true);
        variant(id, "V-1", "100.00", true, 1);
        variant(id, "V-2", "50.00", false, 1);

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[*].sku", contains("V-1")));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mvc.perform(get("/api/products/{id}", 987654321))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void inactiveProductIsNotFound() throws Exception {
        long id = product("stapovi", "Inactive Rod", "Acme", false);
        variant(id, "I-1", "100.00", true, 1);

        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void productWithoutActiveVariantsIsNotFound() throws Exception {
        long id = product("stapovi", "Empty Rod", "Acme", true);
        variant(id, "E-1", "100.00", false, 1);

        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidIdsWithProblemDetail() throws Exception {
        for (String invalid : new String[] {"abc", "0", "-1"}) {
            mvc.perform(get("/api/products/{id}", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void detailUsesTwoSqlStatementsRegardlessOfVariantCount() throws Exception {
        long id = product("stapovi", "Many Variants", "Acme", true);
        for (int i = 1; i <= 8; i++) {
            variant(id, "M-" + i, "100.00", true, i % 2);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants", hasSize(8)));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
