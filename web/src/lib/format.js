export function formatTime(timestamp) {
  if (!timestamp) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(timestamp));
}

export function maskPassword(password, shouldReveal) {
  if (!password) return "No password stored";
  return shouldReveal ? password : "•".repeat(Math.max(8, password.length));
}