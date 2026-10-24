import { readdir, readFile, rm } from "node:fs/promises";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import { ROOT } from "./config.ts";
import { normalize } from "./normalize.ts";
import type { IndexSong } from "./search-index.ts";

/** Room's exported schema for the app's SearchDatabase, written when the :data module builds. */
const SCHEMA_DIR = path.resolve(ROOT, "..", "..", "data", "schemas", "com.mukhda.data.search.SearchDatabase");

interface RoomSchema {
  database: {
    version: number;
    entities: { tableName: string; createSql: string; contentSyncTriggers?: string[]; indices?: { createSql: string }[] }[];
    setupQueries: string[];
  };
}

/** The newest exported schema: what the app's code expects to open. */
async function latestSchema(): Promise<RoomSchema["database"]> {
  const versions = (await readdir(SCHEMA_DIR)).map((f) => Number(/^(\d+)\.json$/.exec(f)?.[1])).filter(Number.isInteger);
  if (versions.length === 0) throw new Error(`No SearchDatabase schema in ${SCHEMA_DIR}. Build the app's :data module first.`);
  const file = path.join(SCHEMA_DIR, `${Math.max(...versions)}.json`);
  return (JSON.parse(await readFile(file, "utf8")) as RoomSchema).database;
}

/**
 * Writes the app's search index as a ready-built SQLite database, exactly as Room would create
 * it (tables, FTS4 sync triggers, identity hash and version all from the exported schema), so
 * the app opens it with `createFromAsset` instead of importing JSON on the phone. The normalised
 * columns use [normalize], the same rules as the app's SearchNormalizer.
 */
export async function writeSearchDb(file: string, songs: IndexSong[], indexVersion: number): Promise<void> {
  const schema = await latestSchema();
  await rm(file, { force: true });
  const db = new DatabaseSync(file);
  try {
    for (const entity of schema.entities) {
      db.exec(entity.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
      for (const index of entity.indices ?? []) db.exec(index.createSql.replaceAll("${TABLE_NAME}", entity.tableName));
      for (const trigger of entity.contentSyncTriggers ?? []) db.exec(trigger);
    }
    for (const query of schema.setupQueries) db.exec(query);

    const insert = db.prepare(
      "INSERT OR IGNORE INTO search_songs (songId, title, filmId, filmTitle, year, titleNorm, filmNorm) VALUES (?, ?, ?, ?, ?, ?, ?)",
    );
    db.exec("BEGIN");
    for (const s of songs) insert.run(s.songId, s.title, s.filmId, s.filmTitle, s.year, normalize(s.title), normalize(s.filmTitle));
    db.prepare("INSERT INTO search_index_meta (id, version) VALUES (0, ?)").run(indexVersion);
    db.exec("COMMIT");
    db.exec(`PRAGMA user_version = ${schema.version}`);
    db.exec("VACUUM");
  } finally {
    db.close();
  }
}
