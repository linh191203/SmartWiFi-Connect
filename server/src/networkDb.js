const Database = require("better-sqlite3");
const path = require("path");
const fs = require("fs");

const DB_DIR = path.resolve(process.env.DB_DIR || path.join(__dirname, "..", "data"));
const DB_PATH = path.join(DB_DIR, "networks.db");

let _db = null;

function getDb() {
  if (_db) return _db;

  // Ensure data directory exists
  fs.mkdirSync(DB_DIR, { recursive: true });

  _db = new Database(DB_PATH);
  _db.pragma("journal_mode = WAL");
  _db.pragma("foreign_keys = ON");

  _db.exec(`
    CREATE TABLE IF NOT EXISTS networks (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      userId      TEXT    NOT NULL DEFAULT 'anonymous',
      ssid        TEXT    NOT NULL,
      password    TEXT,
      security    TEXT    NOT NULL DEFAULT 'WPA/WPA2',
      sourceFormat TEXT   NOT NULL DEFAULT 'manual_entry',
      passwordSaved INTEGER NOT NULL DEFAULT 0,
      passwordEncrypted INTEGER NOT NULL DEFAULT 0,
      lastConnectedAtMillis INTEGER NOT NULL,
      createdAtMillis       INTEGER NOT NULL,
      UNIQUE(userId, ssid)
    );

    CREATE INDEX IF NOT EXISTS idx_networks_userId
      ON networks(userId);

    CREATE INDEX IF NOT EXISTS idx_networks_userId_lastConn
      ON networks(userId, lastConnectedAtMillis DESC);
  `);

  return _db;
}

// ── helpers ──────────────────────────────────────────────────────────────────

function rowToRecord(row) {
  if (!row) return null;
  return {
    id: row.id,
    userId: row.userId,
    ssid: row.ssid,
    password: row.password ?? null,
    security: row.security,
    sourceFormat: row.sourceFormat,
    passwordSaved: row.passwordSaved === 1,
    passwordEncrypted: row.passwordEncrypted === 1,
    lastConnectedAtMillis: row.lastConnectedAtMillis,
    createdAtMillis: row.createdAtMillis,
  };
}

// ── public API ────────────────────────────────────────────────────────────────

/**
 * Upsert a network for a user. If the SSID already exists for that user,
 * update it; otherwise insert.
 * @returns {object} The saved record with its assigned id.
 */
function saveNetwork({ userId, ssid, password, security, sourceFormat, passwordSaved, passwordEncrypted }) {
  const db = getDb();
  const now = Date.now();

  const existing = db.prepare(
    "SELECT id, createdAtMillis FROM networks WHERE userId = ? AND ssid = ?"
  ).get(userId, ssid);

  if (existing) {
    db.prepare(`
      UPDATE networks SET
        password = ?,
        security = ?,
        sourceFormat = ?,
        passwordSaved = ?,
        passwordEncrypted = ?,
        lastConnectedAtMillis = ?
      WHERE userId = ? AND ssid = ?
    `).run(
      password ?? null,
      security,
      sourceFormat,
      passwordSaved ? 1 : 0,
      passwordEncrypted ? 1 : 0,
      now,
      userId,
      ssid,
    );
    return rowToRecord(
      db.prepare("SELECT * FROM networks WHERE userId = ? AND ssid = ?").get(userId, ssid)
    );
  }

  const info = db.prepare(`
    INSERT INTO networks
      (userId, ssid, password, security, sourceFormat, passwordSaved, passwordEncrypted, lastConnectedAtMillis, createdAtMillis)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(
    userId,
    ssid,
    password ?? null,
    security,
    sourceFormat,
    passwordSaved ? 1 : 0,
    passwordEncrypted ? 1 : 0,
    now,
    now,
  );

  return rowToRecord(
    db.prepare("SELECT * FROM networks WHERE id = ?").get(info.lastInsertRowid)
  );
}

/**
 * List all networks for a user, newest first.
 */
function listNetworks(userId) {
  return getDb()
    .prepare("SELECT * FROM networks WHERE userId = ? ORDER BY lastConnectedAtMillis DESC")
    .all(userId)
    .map(rowToRecord);
}

/**
 * Get a single network by its row id for a given user.
 */
function getNetworkById(id, userId) {
  return rowToRecord(
    getDb().prepare("SELECT * FROM networks WHERE id = ? AND userId = ?").get(id, userId)
  );
}

/**
 * Delete a network by its row id for a given user.
 * @returns {boolean} true if a row was deleted.
 */
function deleteNetwork(id, userId) {
  const info = getDb()
    .prepare("DELETE FROM networks WHERE id = ? AND userId = ?")
    .run(id, userId);
  return info.changes > 0;
}

/**
 * Delete all networks for a user.
 * @returns {number} rows deleted.
 */
function clearNetworks(userId) {
  const info = getDb()
    .prepare("DELETE FROM networks WHERE userId = ?")
    .run(userId);
  return info.changes;
}

/**
 * Close the database (useful for testing).
 */
function closeDb() {
  if (_db) {
    _db.close();
    _db = null;
  }
}

module.exports = {
  saveNetwork,
  listNetworks,
  getNetworkById,
  deleteNetwork,
  clearNetworks,
  closeDb,
};
