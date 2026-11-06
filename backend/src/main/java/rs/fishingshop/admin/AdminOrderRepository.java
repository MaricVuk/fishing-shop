package rs.fishingshop.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.OrderStatus;

interface AdminOrderRepository extends Repository<CustomerOrder, Long> {

    @Query(value = """
            select new rs.fishingshop.admin.AdminOrderRow(
                o.id, o.createdAt, o.status, o.customerName, o.customerEmail, o.total,
                (select count(i) from OrderItem i where i.order = o))
            from CustomerOrder o
            where (:status is null or o.status = :status)
              and (o.id = :orderId
                   or lower(o.customerName) like :pattern escape '\\'
                   or lower(o.customerEmail) like :pattern escape '\\')
            order by o.createdAt desc, o.id desc
            """, countQuery = """
            select count(o)
            from CustomerOrder o
            where (:status is null or o.status = :status)
              and (o.id = :orderId
                   or lower(o.customerName) like :pattern escape '\\'
                   or lower(o.customerEmail) like :pattern escape '\\')
            """)
    Page<AdminOrderRow> search(@Param("status") OrderStatus status, @Param("orderId") Long orderId,
            @Param("pattern") String pattern, Pageable pageable);
}
