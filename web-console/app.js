const state = {
  activeView: "config",
  configured: false,
  config: null,
  runtimeConfig: null,
  lastRun: null,
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

  content.textContent = run.finalAnswer || "模型没有返回文本。";
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

renderRun();
loadConfiguration();
