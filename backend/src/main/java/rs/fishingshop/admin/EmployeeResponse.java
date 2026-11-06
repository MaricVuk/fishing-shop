package rs.fishingshop.admin;

import java.time.Instant;
import rs.fishingshop.employees.Employee;
import rs.fishingshop.employees.EmployeeRole;

record EmployeeResponse(Long id, String email, String fullName, EmployeeRole role, boolean active, Instant createdAt) {

    static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getEmail(), employee.getFullName(), employee.getRole(),
                employee.isActive(), employee.getCreatedAt());
    }
}
