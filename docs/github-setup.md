# Publish and run Sports Arena

Publishing this repository to GitHub stores the source code. Running the Java services
and PostgreSQL requires a server or a hosting provider; GitHub Pages cannot run them.
No commit or push is performed by these instructions automatically.

## What belongs in the repository

Keep application code, automated tests, Maven wrappers, `package-lock.json`, safe
application configs, and `docs/migrations`. Tests do not ship in the Spring Boot
production JAR. Migration scripts and recovery entities are required application
assets, not temporary files.

Build outputs (`target`, `dist`), dependencies (`node_modules`), logs, editor settings,
environment files, local application overrides, and private key files are ignored.
The demo controller lives under `src/test` so its endpoints are not shipped.

## 1. Configure a local environment

Install Git, JDK 21, Maven 3.9+, Node.js 22.12+ (or a compatible newer release),
and PostgreSQL. Create an empty development database named `arena_booking_db`.

Application configs now use environment variables instead of embedded credentials.
Set the following in every terminal that launches a backend service. Enter real
values locally; do not paste secrets into Git, screenshots, or repository settings
that are publicly visible.

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/arena_booking_db'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
# Read the password without writing it into terminal command history.
$env:SPRING_DATASOURCE_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Database password' -AsSecureString)).Password
$env:JWT_SECRET = [System.Net.NetworkCredential]::new('', (Read-Host 'JWT signing secret' -AsSecureString)).Password
$env:WALLET_SERVICE_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'Wallet service key' -AsSecureString)).Password
```

Use a cryptographically random JWT secret and a separate random wallet service key,
each at least 32 ASCII bytes. Use the same JWT secret across auth, venue, and booking;
use the same wallet key across auth and booking. Generate these once in a password
manager and reuse the values across the service terminals. Production credentials
belong in the hosting provider's secret store.

The default schema mode is `validate`, which requires an existing schema. For an
empty, disposable local development database only, set `$env:JPA_DDL_AUTO = 'update'`
while starting the three database services to generate their initial schema. This
does not replace a reviewed production schema migration process. Existing databases
need the applicable migrations and rollout sequence in
[booking-administration.md](booking-administration.md),
[automatic-recovery.md](automatic-recovery.md), and
[wallet-changes.md](wallet-changes.md). Do not blindly rerun one-time migrations.

Optional environment settings:

| Variable | Default / purpose |
| --- | --- |
| `EUREKA_URL` | `http://localhost:8761/eureka/` |
| `FRONTEND_ORIGIN` | `http://localhost:5173`; exact browser origin allowed by the gateway |
| `JPA_DDL_AUTO` | `validate`; avoid automatic schema changes in production |
| `JPA_SHOW_SQL` | `false` |

The safe `application.properties` / `application.yml` files are committed. For local
Spring overrides, an ignored `application-local.properties` can be used with the
`local` profile. A root `.env` is not automatically loaded by Spring Boot.

For the frontend:

```powershell
Copy-Item sports-arena-frontend/.env.example sports-arena-frontend/.env.local
```

Set `VITE_API_BASE_URL` to the gateway's reachable URL. It is public and embedded
at build time. Never put a secret into a `VITE_` variable.

## 2. Verify the source

Run from the repository root. These tests use isolated databases or mocks; Docker
enables the additional wallet PostgreSQL tests (otherwise those tests are skipped).

```powershell
mvn -f auth-service/pom.xml clean verify
mvn -f venue-service/pom.xml clean verify
mvn -f booking-service/pom.xml clean verify
mvn -f discovery-server/pom.xml clean verify
mvn -f api-gateway/pom.xml clean verify
npm.cmd --prefix sports-arena-frontend ci
npm.cmd --prefix sports-arena-frontend run build
```

For local execution, use separate terminals with the backend environment set.
Start discovery-server first, then auth-service and venue-service, then
booking-service, and finally api-gateway:

```powershell
# Run one of these in each terminal, from the repository root.
mvn -f discovery-server/pom.xml spring-boot:run
mvn -f auth-service/pom.xml spring-boot:run
mvn -f venue-service/pom.xml spring-boot:run
mvn -f booking-service/pom.xml spring-boot:run
mvn -f api-gateway/pom.xml spring-boot:run
```

```powershell
npm.cmd --prefix sports-arena-frontend run dev
```

Open `http://localhost:5173`. Gateway: `8080`; venue: `8081`; booking: `8082`;
auth: `8083`; discovery: `8761`. For an actual deployment, restrict service and
discovery ports to the private network, expose the gateway over HTTPS, configure
the frontend origin, and serve the frontend with SPA route fallback to `index.html`.

## 3. Review exactly what will be published

The existing checkout already uses branch `main` and has this remote:
`https://github.com/AvrutPatel/sports-arena-booking-system.git`.
Do not run `git init` again or create a nested repository.

```powershell
git status --short
git remote -v
git diff --check
git diff
git add .
git diff --cached --stat
git diff --cached
# This should print no private environment files, credentials, or build output.
git ls-files '*.pem' '*.key' '*.p12' '*.pfx' '*.jks' '.env' '.env.local' '*/target/*' '*/node_modules/*' '*/dist/*'
```

Review the staged diff for credentials as well as file names. The removed local
hardcoded database password and JWT secret should be replaced before deployment;
if they were ever shared or published, rotate them. `.gitignore` does not erase Git
history. If a real secret is already in a previous commit, remove it from history
using a deliberate cleanup process before publishing; deleting it in a new commit
does not remove the old copy.

## 4. Commit and push

Sign in to GitHub using Git Credential Manager's browser prompt when Git asks.
Use a token through the credential manager if required; never put a token into
the remote URL or a command that may be saved in shell history.

```powershell
git fetch origin
git log --oneline --left-right HEAD...origin/main
git commit -m "Prepare booking platform for GitHub with recovery and booking safeguards"
```

If the log shows commits on `origin/main` missing locally, first run
`git rebase origin/main`, resolve any conflicts, and rerun affected checks.
If GitHub has no `main` branch yet, the comparison can be skipped.

```powershell
git push -u origin main
git status
```

Do not force-push to resolve a rejected push. Fetch and integrate the remote
changes. Open the repository and confirm the README, safe application configs,
source, tests, and migrations are present and secrets/build outputs are absent.

## Cleanup performed

- Removed unused Vite starter logos, icon sprite, styles, and template README.
- Removed empty generated `contextLoads` test classes and generated Spring HELP files.
- Moved the auth demo controller into test sources; kept the real security,
  concurrency, wallet, recovery, ownership, and migration tests.
- Expanded ignore rules and replaced hardcoded config credentials with required
  environment variables; included safe defaults for a fresh clone.
- Made the frontend gateway URL configurable at build time.

The source is prepared for publication; these steps do not claim that deployment
infrastructure, backups, monitoring, or live database migrations have been provisioned.
