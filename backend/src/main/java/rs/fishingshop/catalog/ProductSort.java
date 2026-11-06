package rs.fishingshop.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.jpa.domain.JpaSort;

enum ProductSort {

    NAME("name", JpaSort.unsafe(Direction.ASC, "p.name").and(JpaSort.unsafe(Direction.ASC, "p.id"))),
    PRICE_ASC("price_asc", JpaSort.unsafe(Direction.ASC, "min(v.price)").and(JpaSort.unsafe(Direction.ASC, "p.id"))),
    PRICE_DESC("price_desc", JpaSort.unsafe(Direction.DESC, "min(v.price)").and(JpaSort.unsafe(Direction.ASC, "p.id"))),
    NEWEST("newest", JpaSort.unsafe(Direction.DESC, "p.createdAt").and(JpaSort.unsafe(Direction.ASC, "p.id")));

    private final String parameter;
    private final Sort sort;

    ProductSort(String parameter, Sort sort) {
        this.parameter = parameter;
        this.sort = sort;
    }

    Sort toSort() {
        return sort;
    }

    static ProductSort fromParameter(String parameter) {
        for (ProductSort value : values()) {
            if (value.parameter.equals(parameter)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown sort: " + parameter);
    }
}
