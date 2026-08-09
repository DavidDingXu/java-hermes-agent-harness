const state = {
  activeView: "config",
  configured: false,
  config: null,
  runtimeConfig: null,
  lastRun: null,
  operations: null,
  pendingSkills: [],
};

const viewCopy = {
  config: {
    title: "模型配置",
    description: "配置目标模型和工作区，凭证仅保存在当前 Java 进程内。",
  },
  runtime: {
    title: "运行时配置",
    description: "配置 Agent 规则、Memory、Skills 和工作区工具权限。",
  },
  run: {
    title: "运行任务",
    description: "提交任务后，真实 Main Loop 会调用模型和工作区工具。",
  },
  trajectory: {
    title: "运行轨迹",
    description: "查看本次运行中的模型轮次、工具请求和 Observation。",
  },
  operations: {
    title: "运行状态",
    description: "查看持久化 Session、Memory、Skill 审批和模型调用证据。",
  },
};

const element = (selector) => document.querySelector(selector);
const elements = (selector) => [...document.querySelectorAll(selector)];

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: options.body ? { "Content-Type": "application/json" } : {},
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.error || `请求失败：HTTP ${response.status}`);
  }
  return body;
}

function setView(name) {
  state.activeView = name;
  elements(".view").forEach((view) => view.classList.add("hidden"));
  element(`#${name}-view`).classList.remove("hidden");
  elements("[data-view]").forEach((button) => {
    button.classList.toggle("active", button.dataset.view === name);
  });
  element("#page-title").textContent = viewCopy[name].title;
  element("#page-description").textContent = viewCopy[name].description;
  window.scrollTo({ top: 0, behavior: "auto" });
}

function renderConfiguration() {
  const configured = state.configured;
  element("#sidebar-dot").classList.toggle("configured", configured);
  element("#sidebar-status").textContent = configured ? "Runtime 已配置" : "Runtime 未配置";
  element("#config-state").textContent = configured ? "已配置" : "未配置";
  element("#config-state").className = `state-label${configured ? " success" : ""}`;
  element("#model-state").textContent = `模型：${configured ? state.config.model : "未配置"}`;
  element("#workspace-state").textContent = `工作区：${workspaceLabel(state.config?.workspace)}`;
  element("#run-task").disabled = !configured;

  if (state.config) {
    element("#base-url").value = state.config.baseUrl || "";
    element("#model").value = state.config.model || "";
    element("#workspace").value = state.config.workspace || "";
  }
  element("#api-key").value = "";
  element("#api-key-hint").textContent = configured
    ? "凭证已配置。留空保存会继续使用当前凭证。"
    : "首次配置必须填写，保存后不会再次显示。";
}

function renderRuntimeConfiguration() {
  const config = state.runtimeConfig;
  if (!config) {
    return;
  }
  element("#system-prompt-appendix").value = config.systemPromptAppendix || "";
  element("#project-memory").value = config.projectMemory || "";
  element("#user-memory").value = config.userMemory || "";
  element("#skills-directory").value = config.skillsDirectory || "";
  element("#skills-enabled").checked = config.skillsEnabled;
  element("#file-editing-enabled").checked = config.fileEditingEnabled;
  element("#runtime-config-state").textContent = `${config.loadedSkills.length} 个 Skill`;
  element("#runtime-config-state").className = `state-label${config.loadedSkills.length ? " success" : ""}`;

  const list = element("#skill-list");
  list.replaceChildren();
  if (!config.loadedSkills.length) {
    list.textContent = config.skillsEnabled
      ? "当前目录没有可加载的 Skill。"
      : "Skills 已关闭。";
    list.classList.add("empty");
    return;
  }
  list.classList.remove("empty");
  config.loadedSkills.forEach((skill) => {
    const row = document.createElement("div");
    const title = document.createElement("strong");
    const description = document.createElement("span");
    title.textContent = `${skill.name} · ${skill.version}`;
    description.textContent = skill.description || "未填写描述";
    row.append(title, description);
    list.append(row);
  });
}

function workspaceLabel(workspace) {
  if (!workspace) {
    return "未配置";
  }
  const normalized = workspace.replaceAll("\\", "/").replace(/\/+$/, "");
  return normalized.split("/").pop() || workspace;
}

function setMessage(selector, message, isError = false) {
  const target = element(selector);
  target.textContent = message;
  target.classList.toggle("error", isError);
}

function eventTitle(event) {
  if (event.toolName) {
    return `${event.toolName} · ${event.callId}`;
  }
  if (event.kind === "MODEL_FINAL_ANSWER") {
    return "模型给出最终回答";
  }
  if (event.kind === "USER_MESSAGE") {
    return "用户任务进入 Runtime";
  }
  return event.kind;
}

function eventDetail(event) {
  if (event.arguments) {
    return JSON.stringify(event.arguments, null, 2);
  }
  if (event.content) {
    return `${event.success ? "成功" : "失败"}\n${event.content}`;
  }
  return event.text || "";
}

function appendInlineFormatting(parent, text) {
  const token = /(\*\*[^*\n]+\*\*|`[^`\n]+`)/g;
  let cursor = 0;
  for (const match of text.matchAll(token)) {
    parent.append(document.createTextNode(text.slice(cursor, match.index)));
    const node = document.createElement(match[0].startsWith("**") ? "strong" : "code");
    node.textContent = match[0].startsWith("**")
      ? match[0].slice(2, -2)
      : match[0].slice(1, -1);
    parent.append(node);
    cursor = match.index + match[0].length;
  }
  parent.append(document.createTextNode(text.slice(cursor)));
}

function renderModelAnswer(target, answer) {
  target.replaceChildren();
  let activeList = null;
  let activeListKind = "";

  for (const rawLine of answer.replaceAll("\r\n", "\n").split("\n")) {
    const line = rawLine.trim();
    if (!line) {
      activeList = null;
      activeListKind = "";
      continue;
    }

    const bullet = line.match(/^[-*]\s+(.+)$/);
    const numbered = line.match(/^\d+\.\s+(.+)$/);
    if (bullet || numbered) {
      const listKind = bullet ? "ul" : "ol";
      if (!activeList || activeListKind !== listKind) {
        activeList = document.createElement(listKind);
        activeListKind = listKind;
        target.append(activeList);
      }
      const item = document.createElement("li");
      appendInlineFormatting(item, (bullet || numbered)[1]);
      activeList.append(item);
      continue;
    }

    activeList = null;
    activeListKind = "";
    const heading = line.match(/^#{1,3}\s+(.+)$/);
    const block = document.createElement(heading ? "h3" : "p");
    appendInlineFormatting(block, heading ? heading[1] : line);
    target.append(block);
  }
}

function renderRun() {
  const run = state.lastRun;
  const content = element("#result-content");
  const eventList = element("#event-list");
  if (!run) {
    content.textContent = "配置模型后运行第一个任务。";
    content.classList.add("empty");
    element("#result-meta").textContent = "尚未运行任务";
    element("#run-state").textContent = "等待运行";
    eventList.textContent = "完成一次任务后，这里会出现真实运行轨迹。";
    eventList.classList.add("empty");
    return;
  }

  renderModelAnswer(content, run.finalAnswer || "模型没有返回文本。");
  content.classList.remove("empty");
  element("#result-meta").textContent = `${run.conversationId} · ${run.turnsUsed} 个模型轮次`;
  element("#run-state").textContent = run.finishReason;
  element("#run-state").className = `state-label ${run.finishReason === "FINAL_ANSWER" ? "success" : "error"}`;

  const toolRequests = run.events.filter((event) => event.kind === "TOOL_REQUESTED").length;
  const toolResults = run.events.filter((event) => event.kind === "TOOL_OBSERVED").length;
  const metricValues = [run.turnsUsed, toolRequests, toolResults, run.finishReason];
  elements("#trajectory-metrics strong").forEach((metric, index) => {
    metric.textContent = metricValues[index];
  });

  eventList.classList.remove("empty");
  eventList.replaceChildren();
  run.events.forEach((event) => {
    const row = document.createElement("article");
    row.className = "event-row";
    const kind = document.createElement("div");
    kind.className = "event-kind";
    kind.textContent = event.kind;
    const detail = document.createElement("div");
    detail.className = "event-detail";
    const title = document.createElement("strong");
    title.textContent = eventTitle(event);
    const body = document.createElement("pre");
    body.textContent = eventDetail(event);
    detail.append(title, body);
    row.append(kind, detail);
    eventList.append(row);
  });
}

function renderEvidenceList(selector, entries, emptyText) {
  const list = element(selector);
  list.replaceChildren();
  if (!entries.length) {
    list.textContent = emptyText;
    list.classList.add("empty");
    return;
  }
  list.classList.remove("empty");
  entries.forEach((entry) => {
    const row = document.createElement("div");
    row.textContent = entry;
    list.append(row);
  });
}

function renderOperations() {
  const operations = state.operations || {
    modelCalls: 0,
    inputTokens: 0,
    outputTokens: 0,
    trajectoryRecords: 0,
    projectMemory: [],
    userMemory: [],
  };
  const values = [
    operations.modelCalls,
    operations.inputTokens,
    operations.outputTokens,
    operations.trajectoryRecords,
  ];
  elements("#operations-metrics strong").forEach((metric, index) => {
    metric.textContent = values[index] ?? 0;
  });
  renderEvidenceList(
    "#learned-project-memory",
    operations.projectMemory || [],
    "暂无自动沉淀内容。",
  );
  renderEvidenceList(
    "#learned-user-memory",
    operations.userMemory || [],
    "暂无自动沉淀内容。",
  );
}

function renderPendingSkills() {
  const candidates = state.pendingSkills || [];
  const list = element("#approval-list");
  element("#pending-skill-count").textContent = `${candidates.length} 个待处理`;
  list.replaceChildren();
  if (!candidates.length) {
    list.textContent = "当前没有待审批的 Skill。";
    list.classList.add("empty");
    return;
  }
  list.classList.remove("empty");
  candidates.forEach((candidate) => {
    const row = document.createElement("article");
    row.className = "approval-row";
    const content = document.createElement("div");
    const title = document.createElement("strong");
    const description = document.createElement("span");
    const source = document.createElement("small");
    title.textContent = candidate.name;
    description.textContent = candidate.description;
    source.textContent = `来源：${candidate.sourceId}`;
    content.append(title, description, source);
    const actions = document.createElement("div");
    actions.className = "approval-actions";
    const approve = document.createElement("button");
    approve.type = "button";
    approve.className = "small-button approve";
    approve.dataset.skillId = candidate.id;
    approve.dataset.action = "approve";
    approve.textContent = "批准";
    const reject = document.createElement("button");
    reject.type = "button";
    reject.className = "small-button";
    reject.dataset.skillId = candidate.id;
    reject.dataset.action = "reject";
    reject.textContent = "拒绝";
    actions.append(approve, reject);
    row.append(content, actions);
    list.append(row);
  });
}

async function loadOperations() {
  if (!state.configured) {
    state.operations = null;
    state.pendingSkills = [];
    renderOperations();
    renderPendingSkills();
    return;
  }
  const [operations, pending] = await Promise.all([
    api("/api/operations"),
    api("/api/skills/pending"),
  ]);
  state.operations = operations;
  state.pendingSkills = pending.candidates || [];
  renderOperations();
  renderPendingSkills();
}

async function loadConfiguration() {
  try {
    const config = await api("/api/config");
    const runtimeConfig = await api("/api/runtime-config");
    state.configured = config.configured;
    state.config = config;
    state.runtimeConfig = runtimeConfig;
    renderConfiguration();
    renderRuntimeConfiguration();
    const latestRun = await api("/api/runs/latest");
    if (latestRun.available) {
      state.lastRun = latestRun;
      renderRun();
    }
    await loadOperations();
    setView(config.configured ? "run" : "config");
  } catch (error) {
    setMessage("#config-message", error.message, true);
    element("#sidebar-status").textContent = "服务连接失败";
  }
}

elements("[data-view]").forEach((button) => {
  button.addEventListener("click", () => setView(button.dataset.view));
});

element("#config-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = element("#save-config");
  button.disabled = true;
  setMessage("#config-message", "正在保存...");
  try {
    const config = await api("/api/config", {
      method: "POST",
      body: JSON.stringify({
        baseUrl: element("#base-url").value.trim(),
        model: element("#model").value.trim(),
        apiKey: element("#api-key").value,
        workspace: element("#workspace").value.trim(),
      }),
    });
    state.configured = true;
    state.config = config;
    state.runtimeConfig = await api("/api/runtime-config");
    renderConfiguration();
    renderRuntimeConfiguration();
    await loadOperations();
    setMessage("#config-message", "配置已保存");
    setView("run");
  } catch (error) {
    setMessage("#config-message", error.message, true);
  } finally {
    button.disabled = false;
  }
});

element("#runtime-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = element("#save-runtime-config");
  button.disabled = true;
  setMessage("#runtime-message", "正在重新装配 Runtime...");
  try {
    state.runtimeConfig = await api("/api/runtime-config", {
      method: "POST",
      body: JSON.stringify({
        systemPromptAppendix: element("#system-prompt-appendix").value.trim(),
        projectMemory: element("#project-memory").value.trim(),
        userMemory: element("#user-memory").value.trim(),
        skillsDirectory: element("#skills-directory").value.trim(),
        skillsEnabled: element("#skills-enabled").checked,
        fileEditingEnabled: element("#file-editing-enabled").checked,
      }),
    });
    state.lastRun = null;
    renderRuntimeConfiguration();
    renderRun();
    await loadOperations();
    setMessage("#runtime-message", "运行时配置已生效");
  } catch (error) {
    setMessage("#runtime-message", error.message, true);
  } finally {
    button.disabled = false;
  }
});

element("#task-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!state.configured) {
    setView("config");
    setMessage("#config-message", "请先完成模型配置", true);
    return;
  }
  const button = element("#run-task");
  button.disabled = true;
  element("#run-state").textContent = "运行中";
  element("#run-state").className = "state-label running";
  element("#result-meta").textContent = "模型正在处理任务";
  element("#result-content").textContent = "正在等待模型和工具返回结果...";
  setMessage("#run-message", "任务运行中，请保持页面打开");
  try {
    state.lastRun = await api("/api/runs", {
      method: "POST",
      body: JSON.stringify({
        prompt: element("#prompt").value.trim(),
        maxTurns: Number(element("#max-turns").value),
      }),
    });
    renderRun();
    await loadOperations();
    setMessage("#run-message", "运行完成");
  } catch (error) {
    element("#run-state").textContent = "运行失败";
    element("#run-state").className = "state-label error";
    element("#result-meta").textContent = "Runtime 返回错误";
    element("#result-content").textContent = error.message;
    setMessage("#run-message", error.message, true);
  } finally {
    button.disabled = false;
  }
});

element("#session-search-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const query = element("#session-query").value.trim();
  if (!query) {
    setMessage("#session-message", "请输入检索内容", true);
    return;
  }
  setMessage("#session-message", "检索中...");
  try {
    const result = await api(`/api/sessions/search?q=${encodeURIComponent(query)}`);
    const list = element("#session-results");
    list.replaceChildren();
    if (!result.hits.length) {
      list.textContent = "没有找到匹配的 Session 事件。";
      list.classList.add("empty");
    } else {
      list.classList.remove("empty");
      result.hits.forEach((hit) => {
        const row = document.createElement("article");
        row.className = "session-row";
        const meta = document.createElement("strong");
        const snippet = document.createElement("span");
        meta.textContent = `${hit.sessionId} · ${hit.kind} · #${hit.eventIndex}`;
        appendInlineFormatting(snippet, hit.snippet.replace(/\s+/g, " ").trim());
        row.append(meta, snippet);
        list.append(row);
      });
    }
    setMessage("#session-message", `找到 ${result.hits.length} 条记录`);
  } catch (error) {
    setMessage("#session-message", error.message, true);
  }
});

element("#approval-list").addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-skill-id]");
  if (!button) {
    return;
  }
  button.disabled = true;
  try {
    await api(`/api/skills/${encodeURIComponent(button.dataset.skillId)}/${button.dataset.action}`, {
      method: "POST",
      body: "{}",
    });
    await loadOperations();
  } catch (error) {
    button.disabled = false;
    setMessage("#session-message", error.message, true);
  }
});

renderRun();
renderOperations();
renderPendingSkills();
loadConfiguration();
