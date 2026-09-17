# Day 3 — Working in VS Code, pgAdmin and Docker Desktop

From here on, a session starts with one script and one click instead of hand-typing
environment variables into three terminals.

---

## The daily startup sequence

```powershell
.\scripts\start-day.ps1 -WithPgAdmin
```

That one command:

1. Checks Docker Desktop is actually running (and tells you to open it if not)
2. Checks `.env` exists and has no leftover `change-me` values
3. Starts PostgreSQL and **waits until it is genuinely accepting connections**, not merely started
4. **Tests all three service logins against the database** — this catches the
   "password authentication failed" error *before* a service fails to start, and prints the
   exact `ALTER ROLE` command to fix it
5. Optionally starts pgAdmin
6. Prints what's running and what to do next

It only starts containers. It never deletes anything.

### Why step 4 exists

You hit the same failure three times: `password authentication failed for user "..."`.

The cause is always the same. The init script creates the three database roles **once**, on the
very first start with an empty volume, using whatever `.env` held at that moment. Editing `.env`
afterwards changes what the *application* sends, but not what the *database* expects. The two
drift apart.

Rather than discover that from a stack trace, the script probes each login up front and hands
you the fix.

---

## Running services in VS Code

Open the project folder in VS Code (`File > Open Folder`, pick `ecommerce-platform`).

VS Code will offer to install the recommended extensions — accept. The important one is the
**Extension Pack for Java**, which is what understands Maven projects, gives you
autocomplete, and lets you set breakpoints.

The first time you open it, Java support imports all three Maven projects. Watch the status
bar at the bottom; this takes a minute or two and downloads nothing you don't already have.

### Starting a service

Press **Ctrl+Shift+D** (Run and Debug). At the top there's a dropdown with:

- `user-service (8081)`
- `product-service (8082)`
- `order-service (8083)`
- `All three services`

Pick one, press the green play button. That's it — no `mvn` command, no exporting passwords.

Each service gets its own tab in the Debug Console, so you can switch between their logs.
The red stop button in the floating toolbar stops them.

### Where do the passwords come from?

`launch.json` has this line in each configuration:

```json
"envFile": "${workspaceFolder}/.env"
```

VS Code reads your existing `.env` and passes those values to the service as environment
variables. So the same file feeds Docker Compose *and* your IDE runs, and no credential is
ever written into a file that gets committed. `.env` stays git-ignored.

### Debugging (the part that makes this worth it)

Because these are *debug* launches, you can click in the left margin of any `.java` file to set
a breakpoint. Try it: open `OrderService.java`, put a breakpoint on the line

```java
CustomerDto customer = fetchCustomer(request.customerId());
```

then place an order. Execution pauses there, and you can inspect every variable — the request,
what came back from user-service, the products, the computed total — by hovering over them or
using the Variables panel.

Stepping through `createOrder` once teaches more about cross-service orchestration than reading
it ten times.

Useful keys while paused: **F10** step over, **F11** step into, **F5** continue.

---

## pgAdmin: looking at the actual data

pgAdmin is a browser UI for PostgreSQL. It replaces typing `psql` commands with clicking around
tables.

### Starting it

`start-day.ps1 -WithPgAdmin` starts it, or from VS Code: **Ctrl+Shift+P** →
`Tasks: Run Task` → `Start pgAdmin (Docker)`.

Then open **http://localhost:5050**. Log in with the `PGADMIN_EMAIL` and `PGADMIN_PASSWORD`
from your `.env`. First load can take 30 seconds.

### Connecting it to your database (one time only)

1. Right-click **Servers** in the left panel → **Register** → **Server...**
2. **General** tab → Name: `ecommerce` (any label you like)
3. **Connection** tab:
   - Host name/address: **`postgres`**
   - Port: `5432`
   - Maintenance database: `postgres`
   - Username: the `POSTGRES_SUPERUSER` from `.env` (normally `postgres`)
   - Password: the `POSTGRES_SUPERUSER_PASSWORD` from `.env`
   - Tick **Save password**
4. **Save**

**The host is `postgres`, not `localhost`.** This trips everyone up once. pgAdmin is running
*inside* a container, and from in there, `localhost` means "the pgAdmin container itself".
Containers on the same Compose network reach each other by service name — and `postgres` is
what that service is called in `docker-compose.yml`.

(If you instead use a pgAdmin installed directly on Windows, then it's a normal Windows program
and the host *is* `localhost`.)

### What to look at first

Expand `ecommerce` → `Databases`. You'll see the three databases side by side:

```
ecommerce_user_db
ecommerce_product_db
ecommerce_order_db
```

Seeing them as three separate entries is the point of the whole architecture, made visible.

To browse a table: `ecommerce_order_db` → `Schemas` → `public` → `Tables` → right-click
`orders` → **View/Edit Data** → **All Rows**.

Two things worth noticing in that grid:

- **`customer_id` is a bare UUID** with no foreign key. It points into a completely different
  database. pgAdmin will not show you a relationship, because there isn't one.
- In `order_items`, **`product_name` and `unit_price` sit there as plain stored values**. That's
  the snapshot: copied at order time so the order never changes when the catalogue does.

Now try the thing that proves the boundary is real. Open the **Query Tool** (right-click
`ecommerce_order_db` → Query Tool) and run:

```sql
SELECT * FROM products;
```

It fails — that table does not exist in this database. You genuinely *cannot* join orders to
products. The service boundary isn't a convention you're politely observing; the database
enforces it.

Also worth a look: `flyway_schema_history` in any of the three databases. Every migration that
has run, in order, with a checksum. That's how Flyway knows what's already applied.

---

## Shutting down at the end of a session

From VS Code: **Ctrl+Shift+P** → `Tasks: Run Task` → `Stop containers (KEEPS all data)`.

Or directly:

```powershell
docker compose down
```

**Note the absence of `-v`.** `docker compose down` stops the containers but leaves the volumes,
so every customer, product and order you created is still there tomorrow. Adding `-v` would
delete the volumes and wipe all of it.

You don't have to stop anything, though — leaving Docker Desktop running is fine, and the
containers come back on their own.

---

## Quick reference

| I want to | Do this |
|---|---|
| Start the day | `.\scripts\start-day.ps1 -WithPgAdmin` |
| Run the services | Ctrl+Shift+D → "All three services" → play |
| Check everything works | `.\scripts\verify-phase1.ps1` |
| Look at the data | http://localhost:5050 |
| Run the tests | Ctrl+Shift+P → Tasks → "Run all tests" |
| Stop for the day | Ctrl+Shift+P → Tasks → "Stop containers (KEEPS all data)" |
| Swagger | 8081 / 8082 / 8083 + `/swagger-ui.html` |
