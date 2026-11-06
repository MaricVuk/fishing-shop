import { Notice } from "@/components/notice";

export function ComingSoon({ title }: { title: string }) {
  return (
    <>
      <h1 className="text-3xl font-bold">{title}</h1>
      <Notice
        title="Ovaj deo je u izradi"
        description="Stranica je spremna za vašu ulogu, a podaci stižu u sledećoj verziji admin dela."
      />
    </>
  );
}
