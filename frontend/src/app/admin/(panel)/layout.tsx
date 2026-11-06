import { AdminHeader } from "@/components/admin/admin-header";
import { requireEmployee } from "@/lib/admin-access";

export default async function PanelLayout({ children }: LayoutProps<"/admin">) {
  const employee = await requireEmployee();

  return (
    <>
      <AdminHeader employee={employee} />
      <main
        id="sadrzaj"
        tabIndex={-1}
        className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 outline-none sm:px-6 lg:px-8"
      >
        {children}
      </main>
    </>
  );
}
