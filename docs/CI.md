# CI/CD Setup — Project W API Tests

This project includes pipeline definitions for **Jenkins** and **GitHub Actions**. Tests run against a live API; credentials and base URL are injected via secrets or environment.

---

## 1. Jenkins

### Prerequisites

- Jenkins with **Pipeline** and **Maven** support.
- **JDK 21** installed (tool name or `JAVA_HOME`).
- **Maven 3.6+** installed (e.g. "Maven 3.9" in "Manage Jenkins → Global Tool Configuration").

### Pipeline

- The repo root contains a **Jenkinsfile** (declarative pipeline).
- Create a **Pipeline** job, set "Pipeline script from SCM", point to this repo and the default branch.

### Credentials and environment

Tests use `config.properties`; any value can be overridden by **system properties** (e.g. `-Dbase.url=...`). In Jenkins, pass secrets via **Environment variables** or **Credentials** and map them to Maven `-D` options.

**Option A — Environment variables (recommended)**  
In the job: **Configure → Pipeline → Pipeline script** or **Environment variables** (e.g. "Inject environment variables"):

| Variable | Description | Example |
|----------|-------------|--------|
| `BASE_URL` | API base URL | `https://dev.api.ekohamgroup.com` |
| `ADMIN_PASSWORD` | Admin user password | From Jenkins Credentials (Secret text) |
| `EMPLOYEE_PASSWORD` | Employee user password | From Jenkins Credentials (Secret text) |

The Jenkinsfile passes these into Maven as `-Dbase.url=...`, `-Dtest.admin.password=...`, `-Dtest.employee.password=...`.

**Option B — Jenkins Credentials**  
1. **Manage Jenkins → Credentials**: add **Secret text** for admin and employee passwords.  
2. In the Jenkinsfile, use `withCredentials([string(credentialsId: 'admin-pwd', variable: 'ADMIN_PASSWORD')]) { ... }` and reference `ADMIN_PASSWORD` in the `mvn` step (as in Option A).

### Artifacts

- **JUnit/Surefire**: test results are published from `target/surefire-reports/*.xml`.
- **Extent Report**: HTML report is published from `target/extent-reports/` (see Jenkinsfile `publishHTML`).
- **Allure**: set env `ALLURE_PUBLISH=true` and ensure Allure Plugin is installed to publish the Allure report from `target/allure-results`.

### Running a subset of tests

To run a single test class instead of the full suite, override the Maven command in the Jenkinsfile or use a parameter:

```groovy
sh "mvn clean test -B -q -Dtest=ProductTest -Dbase.url=${BASE_URL:-https://dev.api.ekohamgroup.com}"
```

### Jenkins as MCP server (Cursor / AI clients)

To use **Jenkins as the MCP server** so Cursor (or another MCP client) can trigger builds and read status from the IDE:

1. Install the **MCP Server** plugin on Jenkins (2.533+).
2. Create a Jenkins API token and build a Basic auth header.
3. Add Jenkins as an MCP server in Cursor with the streamable HTTP endpoint and auth header.

Full steps, endpoints, and Cursor config: **[Jenkins-MCP.md](Jenkins-MCP.md)**.

---

## 2. GitHub Actions

### Workflow file

- **`.github/workflows/api-tests.yml`** runs on push/PR to `main`/`master` and on `workflow_dispatch`.

### Secrets (Settings → Secrets and variables → Actions)

| Secret | Required | Description |
|--------|----------|-------------|
| `TEST_ADMIN_PASSWORD` | **Yes** | Admin user password (e.g. for ganesh@gmail.com). |
| `TEST_EMPLOYEE_PASSWORD` | **Yes** | Employee user password (e.g. for prathik@gmail.com). |
| `BASE_URL` | No | API base URL. Default: `https://dev.api.ekohamgroup.com`. |

The workflow passes these into Maven as `-Dtest.admin.password=...`, `-Dtest.employee.password=...`, `-Dbase.url=...`. GitHub masks secret values in logs.

### Artifacts

- **Test results**: published with `publish-unit-test-result-action` from `target/surefire-reports/*.xml`.
- **Extent report**: uploaded as artifact `extent-report` from `target/extent-reports/`.
- **Allure results**: uploaded as artifact `allure-results` from `target/allure-results/` (download and run `mvn allure:serve` locally if needed).

---

## 3. Config override reference

`ConfigManager` loads `src/test/resources/config.properties` and overrides with **Java system properties**. So in CI you can pass:

| System property | Example |
|-----------------|--------|
| `base.url` | `https://dev.api.ekohamgroup.com` |
| `test.eid` | `ekoham` |
| `test.admin.email` | `ganesh@gmail.com` |
| `test.admin.password` | (from secret) |
| `test.employee.email` | `prathik@gmail.com` |
| `test.employee.password` | (from secret) |

Example (local or script):

```bash
mvn test -Psuite -Dbase.url=https://dev.api.ekohamgroup.com \
  -Dtest.admin.password="$ADMIN_PASSWORD" \
  -Dtest.employee.password="$EMPLOYEE_PASSWORD"
```

---

## 4. Quick checklist

- [ ] **Jenkins**: JDK 21 + Maven configured; pipeline uses this repo’s Jenkinsfile.
- [ ] **Jenkins**: `BASE_URL`, `ADMIN_PASSWORD`, `EMPLOYEE_PASSWORD` set (or equivalent credentials).
- [ ] **GitHub**: `TEST_ADMIN_PASSWORD`, `TEST_EMPLOYEE_PASSWORD` (and optionally `BASE_URL`) set in repo secrets.
- [ ] API at `base.url` is reachable from the CI runner (network/firewall).
- [ ] Test users (admin + employee) exist in the target environment and passwords match the secrets.
