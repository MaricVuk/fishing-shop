package rs.fishingshop.employees;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("select e from Employee e where lower(e.email) = lower(:email)")
    Optional<Employee> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select e from Employee e order by e.active desc, e.role asc, e.fullName asc, e.id asc")
    List<Employee> findAllForAdmin();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.role = rs.fishingshop.employees.EmployeeRole.OWNER and e.active = true order by e.id")
    List<Employee> lockActiveOwners();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findForUpdate(@Param("id") Long id);
}
