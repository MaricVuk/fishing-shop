package rs.fishingshop;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class VariantLinesApiTests extends CatalogTestSupport {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsCurrentLineDataForRequestedVariants() throws Exception {
        long product = product("stapovi", "Line Rod", "Acme", true);
        variantWithAttributes(product, "LN-240", "{\"length\":\"2.4m\"}", "4500.00", true, 3);
        variantWithAttributes(product, "LN-270", "{\"length\":\"2.7m\"}", "4900.00", true, 0);
        long first = variantId("LN-240");
        long second = variantId("LN-270");

        mvc.perform(get("/api/variants").param("ids", first + "," + second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].variantId").value(first))
                .andExpect(jsonPath("$[0].productId").value(product))
                .andExpect(jsonPath("$[0].productName").value("Line Rod"))
                .andExpect(jsonPath("$[0].brand").value("Acme"))
                .andExpect(jsonPath("$[0].sku").value("LN-240"))
                .andExpect(jsonPath("$[0].attributes.length").value("2.4m"))
                .andExpect(jsonPath("$[0].price").value(4500.0))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[1].inStock").value(false));
    }

    @Test
    void omitsUnknownAndInactiveVariants() throws Exception {
        long visible = product("stapovi", "Visible Line", "Acme", true);
        variant(visible, "VL-1", "100.00", true, 1);
        variant(visible, "VL-2", "100.00", false, 1);
        long hidden = product("stapovi", "Hidden Line", "Acme", false);
        variant(hidden, "VL-3", "100.00", true, 1);

        String ids = String.join(",",
                String.valueOf(variantId("VL-1")),
                String.valueOf(variantId("VL-2")),
                String.valueOf(variantId("VL-3")),
                "987654321");

        mvc.perform(get("/api/variants").param("ids", ids))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sku", contains("VL-1")));
    }

    @Test
    void rejectsInvalidIdLists() throws Exception {
        String tooMany = LongStream.rangeClosed(1, 51).mapToObj(String::valueOf).collect(Collectors.joining(","));

        for (String ids : new String[] {"", "abc", "0", "-5", "1,x", tooMany}) {
            mvc.perform(get("/api/variants").param("ids", ids))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        mvc.perform(get("/api/variants")).andExpect(status().isBadRequest());
    }
}
