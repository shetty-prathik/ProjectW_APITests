# Jenkins as MCP Server — Project W API Tests

Use **Jenkins as the MCP server** so Cursor (or another MCP client) can trigger builds, read status, and fetch logs for the Project W API test pipeline without leaving the IDE.

---

## 1. Prerequisites

- **Jenkins 2.533 or higher**
- Pipeline job for this repo already set up (see [CI.md](CI.md))

---

## 2. Install the MCP Server plugin on Jenkins

1. In Jenkins: **Manage Jenkins → Plugins → Available plugins**.
2. Search for **"MCP Server"** (or **"Model Context Protocol"**).
3. Install **MCP Server** and restart Jenkins if prompted.

Plugin page: [plugins.jenkins.io/mcp-server](https://plugins.jenkins.io/mcp-server)

---

## 3. MCP endpoints (no extra config)

After installation, Jenkins exposes these endpoints (replace `https://your-jenkins.example.com` with your Jenkins URL):

| Transport        | Endpoint                          | Use case                          |
|------------------|-----------------------------------|-----------------------------------|
| **Streamable HTTP** | `https://your-jenkins.example.com/mcp-server/mcp`   | Recommended for Cursor / most clients |
| **SSE**          | `https://your-jenkins.example.com/mcp-server/sse`   | Alternative (e.g. Copilot)        |
| **Stateless**    | `https://your-jenkins.example.com/mcp-server/stateless` | No session; simple clients       |

---

## 4. Authentication

The MCP server uses the same auth as Jenkins (e.g. username + API token).

### 4.1 Create a Jenkins API token

1. In Jenkins: click your **username (top right) → Security**.
2. **Add new token** → name it (e.g. `mcp-cursor`) → **Generate**.
3. Copy the token and store it securely (it is shown only once).

### 4.2 Basic auth header for MCP

MCP clients send **HTTP Basic** auth: `Authorization: Basic <base64(username:token)>`.

**Linux / macOS (Git Bash):**
```bash
echo -n "YOUR_JENKINS_USERNAME:YOUR_API_TOKEN" | base64
```

**Windows (PowerShell):**
```powershell
[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("YOUR_JENKINS_USERNAME:YOUR_API_TOKEN"))
```

Use the output as the value for the `Authorization` header: `Basic <that-output>`.

---

## 5. Connect Cursor to Jenkins MCP server

Cursor uses an MCP config file (e.g. **Cursor Settings → MCP** or project `.cursor/mcp.json`). Add a **streamable HTTP** server pointing at your Jenkins MCP endpoint and the Basic header.

Replace:

- `https://your-jenkins.example.com` → your Jenkins base URL  
- `Basic dXNlcm5hbWU6dG9rZW4=` → your `Basic <base64(username:token)>` from step 4.2  

**Example Cursor MCP config (streamable HTTP):**

```json
{
  "mcpServers": {
    "jenkins": {
      "type": "streamableHttp",
      "url": "https://your-jenkins.example.com/mcp-server/mcp",
      "headers": {
        "Authorization": "Basic dXNlcm5hbWU6dG9rZW4="
      }
    }
  }
}
```

A copyable template is in **`.cursor/mcp.json.example`**. Copy it into your Cursor MCP config (or merge the `jenkins` block into your existing `mcpServers`), then replace `YOUR_JENKINS_HOST` and `YOUR_BASE64_USERNAME_TOKEN` with your values.

If Cursor expects a different key for the transport (e.g. `"transport": "streamableHttp"` or `"command"` for stdio), adjust to match Cursor’s schema; the important part is **url** = Jenkins MCP endpoint and **headers** = Basic auth.

**Alternative — stateless (no session):**

```json
{
  "mcpServers": {
    "jenkins": {
      "type": "http",
      "url": "https://your-jenkins.example.com/mcp-server/stateless",
      "requestInit": {
        "headers": {
          "Authorization": "Basic dXNlcm5hbWU6dG9rZW4="
        }
      }
    }
  }
}
```

After saving, (re)start or refresh MCP in Cursor so it connects to Jenkins.

---

## 5b. GitHub Copilot — MCP server configuration

**Model Context Protocol (MCP)** extends Copilot by connecting it to other tools and services. GitHub and Playwright MCP servers are enabled by default. To add **Jenkins**, paste JSON into your repo’s **Copilot MCP configuration** (where you configure custom MCP servers). You can optionally use **secrets from the repository’s Copilot environment** for the Jenkins auth header.

The Jenkins plugin recommends the **SSE** endpoint for Copilot (streamable HTTP can have issues).

### 1. Create a secret (recommended)

In the repository: **Settings → Copilot → Environment** (or the place where Copilot environment secrets are defined), add a secret:

- **Name:** e.g. `JENKINS_MCP_AUTH`
- **Value:** `Basic <base64(username:token)>` (from section 4.2 above)

### 2. Add Jenkins MCP server JSON

In the Copilot MCP configuration JSON, add:

```json
{
  "mcpServers": {
    "jenkins": {
      "type": "sse",
      "url": "https://YOUR_JENKINS_HOST/mcp-server/sse",
      "headers": {
        "Authorization": "Basic YOUR_BASE64_USERNAME_TOKEN"
      },
      "tools": [
        "getJob",
        "getJobs",
        "triggerBuild",
        "getQueueItem",
        "getBuild",
        "updateBuild",
        "getBuildLog",
        "searchBuildLog",
        "findJobsWithScmUrl",
        "getBuildChangeSets",
        "getBuildScm",
        "getJobScm",
        "getStatus",
        "whoAmI"
      ]
    }
  }
}
```

Replace:
- `YOUR_JENKINS_HOST` → your Jenkins host (e.g. `jenkins.mycompany.com`).
- `YOUR_BASE64_USERNAME_TOKEN` → the output of `echo -n "username:api-token" | base64`.

**Note:** Some MCP config UIs require a `tools` array (list of tool names the server exposes). The list above matches the [Jenkins MCP Server plugin](https://plugins.jenkins.io/mcp-server/) built-in tools. If your UI does not require `tools`, you can omit that property.

If your product supports referencing Copilot environment secrets in the JSON (e.g. `$JENKINS_MCP_AUTH` or a documented placeholder), use that for the `Authorization` value instead of pasting the literal Base64 string.

After saving, Copilot can use Jenkins MCP tools (list jobs, trigger the Project W API tests build, get build status and logs).

---

## 6. What you can do from Cursor (MCP tools)

Once connected, the Jenkins MCP server exposes tools such as:

| Tool            | Purpose |
|-----------------|--------|
| `getJobs`       | List jobs (find your Project W pipeline). |
| `getJob`        | Get job details by full name. |
| `triggerBuild`  | Run a build (optionally with parameters). |
| `getBuild`      | Get build info (e.g. last build of the job). |
| `getBuildLog`   | Get console log lines (with pagination). |
| `getQueueItem`  | Check queue after triggering a build. |
| `getStatus`     | Jenkins health/readiness. |
| `whoAmI`        | Current Jenkins user. |

Example prompts in Cursor:

- *“List Jenkins jobs.”*
- *“Trigger a build of the job that runs the Project W API tests.”*
- *“What’s the status of the last build of the API tests job?”*
- *“Show me the last 100 lines of the console log for that build.”*

Your Pipeline job name is whatever you created in Jenkins (e.g. `Project-W-Tests` or `project-w-tests`). Use `getJobs` to confirm the exact name.

---

## 7. Optional: disable transports or tighten security

On the Jenkins server (e.g. `JAVA_OPTS` or system properties):

- Disable streamable HTTP: `-Dio.jenkins.plugins.mcp.server.Endpoint.disableMcpStreamable=true`
- Disable SSE: `-Dio.jenkins.plugins.mcp.server.Endpoint.disableMcpSse=true`
- Disable stateless: `-Dio.jenkins.plugins.mcp.server.Endpoint.disableMcpStateless=true`
- Enforce Origin header: `-Dio.jenkins.plugins.mcp.server.Endpoint.requireOriginMatch=true`  
  (Origin must match Jenkins root URL.)

---

## 8. Quick checklist

- [ ] Jenkins 2.533+ with **MCP Server** plugin installed.
- [ ] API token created (username + token).
- [ ] Base64 `username:token` computed and stored as `Basic <base64>`.
- [ ] Cursor MCP config updated with Jenkins URL and `Authorization` header.
- [ ] Cursor MCP (re)started; you can ask Cursor to list jobs, trigger the API tests job, and show build status/logs.

For pipeline setup (job, credentials, env vars), see [CI.md](CI.md).
