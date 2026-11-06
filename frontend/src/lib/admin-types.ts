export type EmployeeRole = "OWNER" | "SELLER" | "WAREHOUSE";

export type Employee = {
  id: number;
  email: string;
  fullName: string;
  role: EmployeeRole;
};
