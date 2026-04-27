import { describe, it, expect } from "vitest";
import { formatTime, maskPassword } from "../lib/format";

describe("formatTime", () => {
  it("returns '-' for falsy values", () => {
    expect(formatTime(null)).toBe("-");
    expect(formatTime(0)).toBe("-");
    expect(formatTime(undefined)).toBe("-");
  });

  it("returns a formatted date string for a valid timestamp", () => {
    const ts = new Date("2024-06-15T10:30:00").getTime();
    const result = formatTime(ts);
    expect(typeof result).toBe("string");
    expect(result.length).toBeGreaterThan(0);
    expect(result).not.toBe("-");
  });
});

describe("maskPassword", () => {
  it("returns 'No password stored' for empty/null password", () => {
    expect(maskPassword(null, false)).toBe("No password stored");
    expect(maskPassword("", false)).toBe("No password stored");
    expect(maskPassword(undefined, false)).toBe("No password stored");
  });

  it("returns the actual password when shouldReveal is true", () => {
    expect(maskPassword("secret123", true)).toBe("secret123");
  });

  it("returns bullet characters when shouldReveal is false", () => {
    const masked = maskPassword("secret123", false);
    expect(masked).toMatch(/^•+$/);
  });

  it("mask length is at least 8 characters", () => {
    const masked = maskPassword("abc", false);
    expect(masked.length).toBeGreaterThanOrEqual(8);
  });

  it("mask length matches password length when password is longer than 8", () => {
    const password = "averylongpassword";
    const masked = maskPassword(password, false);
    expect(masked.length).toBe(password.length);
  });
});
