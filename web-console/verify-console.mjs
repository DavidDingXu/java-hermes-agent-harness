import { readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const consoleDir = fileURLToPath(new URL(".", import.meta.url));
const indexPath = join(consoleDir, "index.html");
const html = readFileSync(indexPath, "utf8");

const requiredText = [
  "网关任务",
  "定时任务",
  "人工审批",
  "运行轨迹",
  "自进化候选",
  "Skill 候选",
  "Memory 候选",
  "conversationId",
  "sessionKey",
  "finishReason",
];

for (const text of requiredText) {
  if (!html.includes(text)) {
    throw new Error(`missing console text: ${text}`);
  }
}

const requiredHooks = [
  "data-view=\"gateway\"",
  "data-view=\"cron\"",
  "data-view=\"approval\"",
  "data-view=\"trajectory\"",
  "data-view=\"improvement\"",
  "data-action=\"approve\"",
  "data-action=\"deny\"",
  "const runtimeSnapshot",
  "function render",
];

for (const hook of requiredHooks) {
  if (!html.includes(hook)) {
    throw new Error(`missing console hook: ${hook}`);
  }
}

if (statSync(indexPath).size < 12_000) {
  throw new Error("index.html is too small to contain the complete console surface");
}

console.log("web console check passed");
