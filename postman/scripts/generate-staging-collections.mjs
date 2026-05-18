#!/usr/bin/env node
/**
 * Generate postman/.generation/{feature}.postman_collection.json from controllers.
 * Usage: node postman/scripts/generate-staging-collections.mjs
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "../..");
const CONTROLLER_ROOT = path.join(REPO_ROOT, "src/main/java/com/reviewflow");
const GENERATION_DIR = path.join(REPO_ROOT, "postman/.generation");

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

const CONTROLLER_FEATURE = {
  auth: ["auth/controller"],
  user: ["user/controller"],
  course: ["course/controller"],
  assignment: ["assignment/controller"],
  team: ["team/controller"],
  submission: ["submission/controller"],
  evaluation: ["evaluation/controller"],
  grading: ["grading/controller"],
  extension: ["extension/controller"],
  notification: ["notification/controller"],
  announcement: ["announcement/controller"],
  messaging: ["messaging/controller"],
  discussion: ["discussion/controller"],
  admin: ["admin/controller"],
  system: ["system/controller"],
};

const SKIP_CONTROLLERS = new Set(["DocsController.java"]);

const ENVELOPE_SUCCESS = [
  "pm.test('Status code', () => pm.expect(pm.response.code).to.be.oneOf([200, 201, 204]));",
  "const body = pm.response.json();",
  "pm.test('Envelope success', () => pm.expect(body.success).to.eql(true));",
];

const ENVELOPE_AUTH_FAIL = [
  "pm.test('Status 401', () => pm.response.to.have.status(401));",
  "const body = pm.response.json();",
  "pm.test('Envelope failure', () => pm.expect(body.success).to.eql(false));",
  "pm.test('Error code', () => pm.expect(body.error.code).to.eql('UNAUTHORIZED'));",
];

const ENVELOPE_VALIDATION = [
  "pm.test('Status 400', () => pm.response.to.have.status(400));",
  "const body = pm.response.json();",
  "pm.test('Envelope failure', () => pm.expect(body.success).to.eql(false));",
];

const ENVELOPE_FORBIDDEN = [
  "pm.test('Status 403', () => pm.response.to.have.status(403));",
  "const body = pm.response.json();",
  "pm.test('Envelope failure', () => pm.expect(body.success).to.eql(false));",
  "pm.test('Error code', () => pm.expect(body.error.code).to.eql('FORBIDDEN'));",
];

const ENVELOPE_EDGE = [
  "pm.test('Status 4xx', () => pm.expect(pm.response.code).to.be.oneOf([400, 404, 409, 422]));",
  "const body = pm.response.json();",
  "pm.test('Envelope failure', () => pm.expect(body.success).to.eql(false));",
];

function testEvent(exec) {
  return [
    {
      listen: "test",
      script: { type: "text/javascript", exec },
    },
  ];
}

function parseController(filePath) {
  const content = fs.readFileSync(filePath, "utf8");
  const classMatch = content.match(/class\s+(\w+)/);
  if (!classMatch) return null;
  if (SKIP_CONTROLLERS.has(path.basename(filePath))) return null;

  const baseMatch = content.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?["'{]([^"'}]+)/);
  let base = baseMatch ? baseMatch[1] : "/api/v1";
  if (base === "/docs") return null;
  if (base === "/system") base = "/api/v1/system";
  if (!base.startsWith("/api/v1") && !base.startsWith("/system")) {
    if (base.startsWith("/")) base = `/api/v1${base}`;
  }

  const mappingRe =
    /@(Get|Post|Put|Patch|Delete)Mapping\s*(?:\(\s*(?:value\s*=\s*)?)?["'{]?([^"')\s,]*)/g;
  const endpoints = [];
  let m;
  while ((m = mappingRe.exec(content)) !== null) {
    let sub = m[2] || "";
    if (sub.startsWith("{")) continue;
    const method = m[1].toUpperCase();
    const fullPath = joinPaths(base, sub);
    endpoints.push({ method, path: fullPath });
  }
  return { className: classMatch[1], base, endpoints };
}

function joinPaths(base, sub) {
  const b = base.replace(/\/$/, "");
  if (!sub) return b;
  const s = sub.startsWith("/") ? sub : `/${sub}`;
  return `${b}${s}`.replace(/\/+/g, "/");
}

function pathToPostman(pathStr) {
  const withoutApi = pathStr.replace(/^\/api\/v1\/?/, "");
  const segments = withoutApi.split("/").filter(Boolean);
  return {
    raw: `{{baseUrl}}/${segments.join("/")}`,
    host: ["{{baseUrl}}"],
    path: segments,
  };
}

function endpointLabel(method, pathStr) {
  const rel = pathStr.replace(/^\/api\/v1\/?/, "/");
  return `${method} ${rel.startsWith("/") ? rel : `/${rel}`}`;
}

function substituteEnvVars(pathStr) {
  return pathStr
    .replace(/\{courseId\}/g, "{{courseId}}")
    .replace(/\{id\}/g, "{{resourceId}}")
    .replace(/\{assignmentId\}/g, "{{assignmentId}}")
    .replace(/\{teamId\}/g, "{{teamId}}")
    .replace(/\{submissionId\}/g, "{{submissionId}}")
    .replace(/\{evaluationId\}/g, "{{evaluationId}}")
    .replace(/\{discussionId\}/g, "{{discussionId}}")
    .replace(/\{conversationId\}/g, "{{conversationId}}")
    .replace(/\{messageId\}/g, "{{messageId}}")
    .replace(/\{postId\}/g, "{{postId}}")
    .replace(/\{userId\}/g, "{{userId}}")
    .replace(/\{studentId\}/g, "{{studentId}}")
    .replace(/\{jobId\}/g, "{{jobId}}")
    .replace(/\{criterionId\}/g, "{{criterionId}}")
    .replace(/\{cacheName\}/g, "{{cacheName}}")
    .replace(/\{targetUserId\}/g, "{{targetUserId}}");
}

function buildRequest(method, pathStr, variant) {
  const urlPath = substituteEnvVars(pathStr);
  const url = pathToPostman(urlPath);
  const req = { method, header: [], url };

  const needsJson = ["POST", "PUT", "PATCH"].includes(method);
  const isMultipart =
    pathStr.includes("avatar") ||
    pathStr.includes("import") ||
    (pathStr.includes("submissions") && method === "POST" && !pathStr.includes("/pdf"));

  if (variant === "happy" && needsJson && !isMultipart) {
    req.header.push({ key: "Content-Type", value: "application/json" });
    req.body = { mode: "raw", raw: "{}" };
  }
  if (variant === "validation" && needsJson) {
    req.header.push({ key: "Content-Type", value: "application/json" });
    req.body = { mode: "raw", raw: "{}" };
  }
  if (isMultipart && variant === "happy") {
    req.body = {
      mode: "formdata",
      formdata: [{ key: "file", type: "file", src: [] }],
    };
  }
  if (method === "GET" && pathStr.match(/\/(courses|notifications|discussions|conversations|assignments|students|audit)/)) {
    url.raw += url.raw.includes("?") ? "" : "?page=0&size=20";
    if (!url.path.includes("page")) {
      url.query = [
        { key: "page", value: "0" },
        { key: "size", value: "20" },
      ];
    }
  }

  req.description = `**Variant:** ${variant}\n**Path:** ${pathStr}\n**Setup:** Use 00_SETUP login cookies; Hashids via environment.`;
  return req;
}

function buildSubfolder(name, method, pathStr, tests) {
  const variantMap = {
    "Happy path": "happy",
    "Auth failure": "auth",
    "Validation failure": "validation",
    "Role access": "role",
    "Domain edge cases": "edge",
  };
  const req =
    name === "Auth failure"
      ? buildRequest(method, pathStr, "auth")
      : buildRequest(method, pathStr, variantMap[name] || "happy");

  if (name === "Auth failure" && method === "GET") {
    req.description += "\n**Expect:** 401 without session cookie.";
  }
  if (name === "Role access") {
    req.description += "\n**Expect:** 403 FORBIDDEN with wrong-role session.";
  }

  return { name, request: req, event: testEvent(tests) };
}

function buildEndpointFolder(ep, specHint) {
  const { method, path: pathStr } = ep;
  const isPublic =
    pathStr.includes("/auth/login") ||
    pathStr.includes("/auth/password-reset");

  const subfolders = [
    buildSubfolder("Happy path", method, pathStr, [...ENVELOPE_SUCCESS]),
    buildSubfolder(
      "Auth failure",
      method,
      pathStr,
      isPublic && method === "POST" && pathStr.includes("/login")
        ? [
            "pm.test('Status 401', () => pm.response.to.have.status(401));",
            "const body = pm.response.json();",
            "pm.test('Envelope failure', () => pm.expect(body.success).to.eql(false));",
          ]
        : [...ENVELOPE_AUTH_FAIL]
    ),
    buildSubfolder("Validation failure", method, pathStr, [...ENVELOPE_VALIDATION]),
    buildSubfolder("Role access", method, pathStr, [...ENVELOPE_FORBIDDEN]),
    buildSubfolder("Domain edge cases", method, pathStr, [...ENVELOPE_EDGE]),
  ];

  return {
    name: endpointLabel(method, pathStr),
    description: specHint || `Generated from controller — ${endpointLabel(method, pathStr)}`,
    item: subfolders,
  };
}

function collectEndpointsForFeature(feature) {
  const dirs = CONTROLLER_FEATURE[feature] || [];
  const endpoints = [];
  const seen = new Set();

  for (const relDir of dirs) {
    const dir = path.join(CONTROLLER_ROOT, relDir);
    if (!fs.existsSync(dir)) continue;
    for (const file of fs.readdirSync(dir)) {
      if (!file.endsWith("Controller.java")) continue;
      const parsed = parseController(path.join(dir, file));
      if (!parsed) continue;
      for (const ep of parsed.endpoints) {
        const key = `${ep.method} ${ep.path}`;
        if (seen.has(key)) continue;
        seen.add(key);
        endpoints.push(ep);
      }
    }
  }
  endpoints.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
  return endpoints;
}

function writeStaging(feature, endpoints) {
  const collection = {
    info: {
      name: `ReviewFlow — ${feature} (staging)`,
      description: `Staging fragment for ${feature}. Merged by: cd postman && npm run merge`,
      schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    item: endpoints.map((ep) => buildEndpointFolder(ep)),
  };

  const outPath = path.join(GENERATION_DIR, `${feature}.postman_collection.json`);
  fs.writeFileSync(outPath, JSON.stringify(collection, null, 2) + "\n", "utf8");
  return { feature, count: endpoints.length, path: outPath };
}

function main() {
  if (!fs.existsSync(GENERATION_DIR)) {
    fs.mkdirSync(GENERATION_DIR, { recursive: true });
  }

  const stats = [];
  for (const feature of FEATURE_ORDER) {
    const endpoints = collectEndpointsForFeature(feature);
    if (endpoints.length === 0) {
      console.log(`— ${feature}/controller/ not found or empty, skipping`);
      continue;
    }
    const result = writeStaging(feature, endpoints);
    stats.push(result);
    console.log(
      `✓ ${feature} — ${result.count} endpoints (staging: ${path.basename(result.path)})`
    );
  }
  return stats;
}

const stats = main();
console.log(`\nGenerated ${stats.length} staging files in postman/.generation/`);
