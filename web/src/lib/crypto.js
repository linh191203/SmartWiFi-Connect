/**
 * Password encryption utilities using the Web Crypto API (AES-GCM + PBKDF2).
 * No external dependencies. All operations are async.
 *
 * Encrypted payload format (base64 of JSON):
 *   { salt: number[], iv: number[], ciphertext: number[] }
 *
 * Security properties:
 *   - AES-GCM 256-bit authenticated encryption
 *   - PBKDF2-SHA256 key derivation with 100,000 iterations
 *   - Random 16-byte salt + 12-byte IV per encryption
 *   - Passphrase never stored anywhere (in-memory only)
 */

const PBKDF2_ITERATIONS = 100_000;
const KEY_BITS = 256;

async function deriveKey(passphrase, salt) {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    enc.encode(passphrase),
    "PBKDF2",
    false,
    ["deriveKey"],
  );
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", salt, iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
    keyMaterial,
    { name: "AES-GCM", length: KEY_BITS },
    false,
    ["encrypt", "decrypt"],
  );
}

/**
 * Encrypt a plaintext password with a passphrase.
 * @param {string} passphrase - User-supplied passphrase (never stored)
 * @param {string} plaintext  - Password to encrypt
 * @returns {Promise<string>}  - Base64-encoded encrypted payload
 */
export async function encryptPassword(passphrase, plaintext) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(passphrase, salt);
  const enc = new TextEncoder();
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    enc.encode(plaintext),
  );
  const payload = {
    salt: Array.from(salt),
    iv: Array.from(iv),
    ciphertext: Array.from(new Uint8Array(ciphertext)),
  };
  return btoa(JSON.stringify(payload));
}

/**
 * Decrypt an encrypted password payload.
 * @param {string} passphrase    - User-supplied passphrase
 * @param {string} encryptedData - Base64-encoded payload from encryptPassword
 * @returns {Promise<string>}     - Decrypted plaintext password
 * @throws {Error} If passphrase is wrong or data is corrupted
 */
export async function decryptPassword(passphrase, encryptedData) {
  try {
    const { salt, iv, ciphertext } = JSON.parse(atob(encryptedData));
    const key = await deriveKey(passphrase, new Uint8Array(salt));
    const decrypted = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: new Uint8Array(iv) },
      key,
      new Uint8Array(ciphertext),
    );
    return new TextDecoder().decode(decrypted);
  } catch {
    throw new Error("Incorrect passphrase or corrupted data");
  }
}

/**
 * Check whether a stored password value is an encrypted payload.
 * @param {string|null} value
 * @returns {boolean}
 */
export function isEncryptedPayload(value) {
  if (!value || typeof value !== "string") return false;
  try {
    const parsed = JSON.parse(atob(value));
    return (
      parsed !== null &&
      typeof parsed === "object" &&
      Array.isArray(parsed.salt) &&
      Array.isArray(parsed.iv) &&
      Array.isArray(parsed.ciphertext)
    );
  } catch {
    return false;
  }
}
