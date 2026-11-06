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
class CatalogApiTests extends CatalogTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void listsSeededCategories() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'stapovi')]").exists())
                .andExpect(jsonPath("$[?(@.slug == 'varalice')]").exists());
    }

    @Test
    void productSummaryHasLowestActivePriceAndStockFlag() throws Exception {
        long rod = product("stapovi", "Test Rod", "Acme", true);
        variant(rod, "ROD-1", "4900.00", true, 2);
        variant(rod, "ROD-2", "4500.00", true, 0);
        variant(rod, "ROD-3", "1000.00", false, 5);
        long reel = product("masinice", "Test Reel", "Acme", true);
        variant(reel, "REEL-1", "3000.00", true, 0);

        mvc.perform(get("/api/products").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[?(@.name == 'Test Rod')].minPrice").value(4500.0))
                .andExpect(jsonPath("$.content[?(@.name == 'Test Rod')].inStock").value(true))
                .andExpect(jsonPath("$.content[?(@.name == 'Test Reel')].inStock").value(false));
    }

    @Test
    void inactiveProductsAndProductsWithoutActiveVariantsAreHidden() throws Exception {
        long inactive = product("stapovi", "Hidden Inactive", "Acme", false);
        variant(inactive, "H-1", "100.00", true, 1);
        long noActiveVariant = product("stapovi", "Hidden NoVariant", "Acme", true);
        variant(noActiveVariant, "H-2", "100.00", false, 1);
        long visible = product("stapovi", "Hidden Visible", "Acme", true);
        variant(visible, "H-3", "100.00", true, 1);

        mvc.perform(get("/api/products").param("q", "hidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Hidden Visible"));
    }

    @Test
    void filtersByCategorySlug() throws Exception {
        variant(product("stapovi", "Cat Rod", "Acme", true), "C-1", "100.00", true, 1);
        variant(product("varalice", "Cat Lure", "Acme", true), "C-2", "100.00", true, 1);

        mvc.perform(get("/api/products").param("q", "cat").param("category", "varalice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Cat Lure"))
                .andExpect(jsonPath("$.content[0].categorySlug").value("varalice"));
    }

    @Test
    void searchIgnoresCaseAndMatchesNameAndBrand() throws Exception {
        variant(product("stapovi", "Catana", "Shimano", true), "S-1", "100.00", true, 1);
        variant(product("stapovi", "Sienna", "Daiwa", true), "S-2", "100.00", true, 1);

        mvc.perform(get("/api/products").param("q", "CATAN"))
                .andExpect(jsonPath("$.content[*].name", contains("Catana")));
        mvc.perform(get("/api/products").param("q", "daiwa"))
                .andExpect(jsonPath("$.content[*].name", contains("Sienna")));
    }

    @Test
    void searchTreatsWildcardCharactersLiterally() throws Exception {
        variant(product("stapovi", "Plain Rod", "Acme", true), "W-1", "100.00", true, 1);

        mvc.perform(get("/api/products").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products").param("q", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortsByPriceAndName() throws Exception {
        variant(product("stapovi", "Sort B", "Acme", true), "O-1", "300.00", true, 1);
        variant(product("stapovi", "Sort A", "Acme", true), "O-2", "900.00", true, 1);
        variant(product("stapovi", "Sort C", "Acme", true), "O-3", "100.00", true, 1);

        mvc.perform(get("/api/products").param("q", "sort").param("sort", "name"))
                .andExpect(jsonPath("$.content[*].name", contains("Sort A", "Sort B", "Sort C")));
        mvc.perform(get("/api/products").param("q", "sort").param("sort", "price_asc"))
                .andExpect(jsonPath("$.content[*].name", contains("Sort C", "Sort B", "Sort A")));
        mvc.perform(get("/api/products").param("q", "sort").param("sort", "price_desc"))
                .andExpect(jsonPath("$.content[*].name", contains("Sort A", "Sort B", "Sort C")));
    }

    @Test
    void paginatesOnTheServer() throws Exception {
        for (int i = 1; i <= 5; i++) {
            variant(product("stapovi", "Page " + i, "Acme", true), "P-" + i, "100.00", true, 1);
        }

        mvc.perform(get("/api/products").param("q", "page").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].name", contains("Page 3", "Page 4")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void rejectsInvalidParametersWithProblemDetail() throws Exception {
        for (String[] invalid : new String[][] {
                {"sort", "bogus"}, {"size", "0"}, {"size", "51"}, {"page", "-1"}, {"category", "Not A Slug"}}) {
            mvc.perform(get("/api/products").param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Test
    void exposesOpenApiDescription() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                .andExpect(jsonPath("$.paths['/api/categories']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}']").exists());
    }

    @Test
    void numberOfSqlStatementsDoesNotDependOnPageSize() throws Exception {
        for (int i = 1; i <= 30; i++) {
            long product = product("stapovi", "Bulk " + i, "Acme", true);
            variant(product, "B-" + i + "-A", "100.00", true, 1);
            variant(product, "B-" + i + "-B", "200.00", true, 0);
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        mvc.perform(get("/api/products").param("q", "bulk").param("size", "5")).andExpect(status().isOk());
        long smallPage = statistics.getPrepareStatementCount();

        statistics.clear();
        mvc.perform(get("/api/products").param("q", "bulk").param("size", "25")).andExpect(status().isOk());
        long largePage = statistics.getPrepareStatementCount();

        assertThat(smallPage).isEqualTo(2);
        assertThat(largePage).isEqualTo(2);
    }
}
