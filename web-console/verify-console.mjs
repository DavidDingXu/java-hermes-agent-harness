import { readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const consoleDir = fileURLToPath(new URL(".", import.meta.url));
const files = {
  html: readFileSync(join(consoleDir, "index.html"), "utf8"),
  css: readFileSync(join(consoleDir, "styles.css"), "utf8"),
  js: readFileSync(join(consoleDir, "app.js"), "utf8"),
};

for (const text of [
  "Hermes Web Console",
  "智能体运行控制台",
  "模型配置",
  "运行时配置",
  "运行任务",
  "运行轨迹",
  "Base URL",
  "API Key",
  "工作区",
  "项目记忆",
  "用户记忆",
  "Skills 目录",
  "运行 Hermes",
]) {
  if (!files.html.includes(text)) {
    throw new Error(`missing console text: ${text}`);
  }
}

for (const hook of [
  'data-view="config"',
  'data-view="runtime"',
  'data-view="run"',
  'data-view="trajectory"',
  'id="config-form"',
  'id="runtime-form"',
  'id="task-form"',
  'id="event-list"',
]) {
  if (!files.html.includes(hook)) {
    throw new Error(`missing console hook: ${hook}`);
  }
}

for (const apiPath of ["/api/config", "/api/runtime-config", "/api/runs", "/api/runs/latest"]) {
  if (!files.js.includes(apiPath)) {
    throw new Error(`missing runtime API integration: ${apiPath}`);
  }
}

if (!files.js.includes("workspaceLabel")) {
  throw new Error("workspace path must use a reader-safe display label");
}

for (const forbidden of ["localStorage", "sessionStorage", "document.cookie", "runtimeSnapshot"]) {
  if (files.js.includes(forbidden) || files.html.includes(forbidden)) {
    throw new Error(`console contains forbidden client-side state: ${forbidden}`);
  }
}

for (const file of ["index.html", "styles.css", "app.js"]) {
  if (statSync(join(consoleDir, file)).size < 1_000) {
    throw new Error(`${file} is unexpectedly small`);
  }
}

console.log("web console check passed");
