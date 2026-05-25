import { apiRequest } from "../../shared/api/http";

export async function getMonthlyStatement(year, month) {
  const response = await apiRequest(
    `/api/statements?year=${year}&month=${month}`,
    {
      method: "GET",
    }
  );

  if (!response.ok) {
    const msg = typeof response.body === "string"
      ? response.body
      : response.body?.message || "Failed to get statement";
    throw new Error(msg);
  }

  return response.body; // This will be the PDF bytes
}

export async function downloadStatement(year, month) {
  const token = localStorage.getItem("dbrisk.accessToken");

  const response = await fetch(`/api/statements?year=${year}&month=${month}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error("Failed to download statement");
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `statement_${year}_${String(month).padStart(2, "0")}.pdf`;
  document.body.appendChild(a);
  a.click();
  window.URL.revokeObjectURL(url);
  document.body.removeChild(a);
}