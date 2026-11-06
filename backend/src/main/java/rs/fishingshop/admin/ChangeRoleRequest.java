package rs.fishingshop.admin;

import jakarta.validation.constraints.NotNull;
import rs.fishingshop.employees.EmployeeRole;

record ChangeRoleRequest(@NotNull EmployeeRole role) {
}
