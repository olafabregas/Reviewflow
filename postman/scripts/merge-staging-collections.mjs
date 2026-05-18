#!/usr/bin/env node
/**
 * Merge postman/.generation/*.postman_collection.json into
 * postman/reviewflow-tests.postman_collection.json
 *
 * Usage (from postman/):  npm run merge
 * Usage (from repo root): node postman/scripts/merge-staging-collections.mjs
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const POSTMAN_DIR = path.resolve(__dirname, "..");
const GENERATION_DIR = path.join(POSTMAN_DIR, ".generation");
const OUTPUT_FILE = path.join(POSTMAN_DIR, "reviewflow-tests.postman_collection.json");
const SETUP_FOLDER_NAME = "00_SETUP";

const FEATURE_ORDER = [
  "auth",
  "user",
  "course",
  "assignment",
  "team",
  "submission",
  "evaluation",
  "grading",
  "extension",
  "notification",
  "announcement",
  "messaging",
  "discussion",
  "admin",
  "system",
];

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function writeJson(filePath, data) {
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2) + "\n", "utf8");
}

function featureKeyFromFilename(filename) {
  return filename.replace(/\.postman_collection\.json$/i, "");
}

function loadStagingFiles() {
  if (!fs.existsSync(GENERATION_DIR)) {
    console.error(`Missing directory: ${GENERATION_DIR}`);
    process.exit(1);
  }

  const byFeature = new Map();
  for (const entry of fs.readdirSync(GENERATION_DIR)) {
    if (!entry.endsWith(".postman_collection.json")) continue;
    const key = featureKeyFromFilename(entry);
    const fullPath = path.join(GENERATION_DIR, entry);
    byFeature.set(key, readJson(fullPath));
  }
  return byFeature;
}

function preserveSetupFolder(existingItems) {
  if (!Array.isArray(existingItems)) return null;
  return existingItems.find((item) => item?.name === SETUP_FOLDER_NAME) ?? null;
}

function buildFeatureFolder(feature, stagingCollection) {
  const items = stagingCollection?.item ?? [];
  const label = feature.charAt(0).toUpperCase() + feature.slice(1);
  return {
    name: label,
    description:
      stagingCollection?.info?.description ??
      `API tests for ${feature} (merged from .generation/${feature}.postman_collection.json)`,
    item: items,
  };
}

function merge() {
  const staging = loadStagingFiles();
  if (staging.size === 0) {
    console.warn("No staging files found in postman/.generation/");
  }

  let base = {
    info: {
      name: "ReviewFlow — Unified API Tests",
      description:
        "Canonical Newman/Postman harness. Staging: postman/.generation/*.json. Merge: npm run merge (in postman/).",
      schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    variable: [{ key: "baseUrl", value: "http://localhost:8081/api/v1", type: "string" }],
    item: [],
  };

  if (fs.existsSync(OUTPUT_FILE)) {
    const existing = readJson(OUTPUT_FILE);
    base.info = { ...base.info, ...existing.info, name: base.info.name };
    if (existing.variable?.length) base.variable = existing.variable;
    if (existing.auth) base.auth = existing.auth;
    if (existing.event) base.event = existing.event;
    const setup = preserveSetupFolder(existing.item);
    if (setup) base.item.push(setup);
  }

  const mergedFeatures = [];
  const missing = [];

  for (const feature of FEATURE_ORDER) {
    const fragment = staging.get(feature);
    if (!fragment) {
      missing.push(feature);
      continue;
    }
    base.item.push(buildFeatureFolder(feature, fragment));
    mergedFeatures.push(feature);
  }

  // Any staging file not in FEATURE_ORDER (append alphabetically)
  const extras = [...staging.keys()]
    .filter((k) => !FEATURE_ORDER.includes(k))
    .sort();
  for (const feature of extras) {
    base.item.push(buildFeatureFolder(feature, staging.get(feature)));
    mergedFeatures.push(feature);
  }

  writeJson(OUTPUT_FILE, base);

  console.log("Merged staging → reviewflow-tests.postman_collection.json");
  console.log(`  Features merged : ${mergedFeatures.length} [${mergedFeatures.join(", ")}]`);
  if (missing.length) {
    console.log(`  Not in staging  : ${missing.length} [${missing.join(", ")}]`);
  }
  console.log(`  Top-level folders : ${base.item.length}`);
}

merge();
