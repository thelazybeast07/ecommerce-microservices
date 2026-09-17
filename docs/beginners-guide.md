# The Complete Beginner's Guide

*Everything in this project explained in plain language — no prior knowledge assumed.*

This is the guide to hand to someone who has never opened a terminal. It explains what the
project is, what every tool does and why it is there, how to set up a machine from nothing,
how to run everything, and what the error messages actually mean.

---

## 1. What you built, in plain words

You built the **backend of an online shop** — the part customers never see.

When you buy something online, your browser shows buttons and pictures. But somewhere, unseen,
software is checking who you are, looking up prices, and recording your order. That unseen
software is the backend. This project is one of those.

It is built as **three small programs that talk to each other**:

| Program | Its one job | Like the person in a real shop who... |
|---|---|---|
| user-service | Knows the customers | ...manages the membership desk |
| product-service | Knows the catalogue | ...stocks the shelves and sets prices |
| order-service | Handles purchases | ...works the till |

Each keeps its own records and minds its own business. When the till needs to know a price, it
*asks* the shelf-stocker — it does not walk over and read the shelf-stocker's notebook.

That style is called **microservices**, and it is how most large companies build software now.
The alternative — one giant program that does everything — is called a monolith. Monoliths are
simpler at first and painful at scale; you built the scalable kind on purpose, to learn it.

## 2. The cast of characters

Every tool installed on your machine, and why it is there.

**The terminal (PowerShell)** — a way of operating the computer by typing instead of clicking.
It feels ancient and it is, but it is how all serious software work gets done, because typed
commands can be saved, repeated and shared exactly. When this guide shows a grey box, that text
gets typed (or pasted) into the terminal.

**Java** — the programming language the three services are written in. Installing it gives your
computer the ability to *run* Java programs, the way installing a DVD player gives a TV the
ability to play discs.

**Maven** — the builder. Your project is hundreds of files plus dozens of libraries written by
other people. Maven downloads those libraries, checks everything fits together, and assembles
it into a runnable program. When you type `mvn`, you are asking the builder to work.

**Spring Boot** — a framework: a huge box of pre-made parts for Java programs. Listening for
web requests, talking to databases, reading configuration — problems every backend has, solved
once so you write only the parts specific to *your* shop.

**PostgreSQL** — the database: the filing cabinet where everything permanent lives. Programs
stop and start; the customers, products and orders must survive that. "Postgres" is one of the
two or three most respected databases in the world, and it is free.

**Docker** — runs software in sealed boxes called **containers**. Your Postgres runs inside
one. Why bother? Because "install a database on Windows" is an afternoon of misery, while
"start the Postgres container" is one command, identical on every machine on earth. The whale
icon near the clock means Docker Desktop is running.

**pgAdmin** — a window into the database. Instead of typing database commands, you click
through tables and see the rows. It changed nothing about the data; it only shows it.

**VS Code** — the editor where the code lives, with an understanding of Java bolted on:
colours, autocomplete, error squiggles, and buttons to run things.

**Swagger** — every service publishes an interactive page listing everything it can do, with
try-it buttons. It is how you poke at the backend without a frontend existing yet. Find it at
`http://localhost:8081/swagger-ui.html` (and 8082, 8083).

## 3. Seven ideas that make everything else make sense

**A server** is just a program that waits to be asked things. Each of your three services is
one. They sit there, listening, until a request arrives.

**localhost and ports.** `localhost` means "this computer". A **port** is a numbered door —
one computer can run many listening programs, so each takes a different door. user-service
answers door 8081, product-service 8082, order-service 8083, Postgres 5432. The address
`http://localhost:8081` reads as: "this computer, door 8081."

**An API** is the fixed list of questions a program agrees to answer and the exact shape of
the answers. "GET /api/v1/products means: give me the product list." Programs can only
cooperate because these agreements exist.

**JSON** is the format those questions and answers travel in — text with curly braces that
both humans and programs can read:

```json
{ "name": "Black cotton t-shirt", "price": 24.99 }
```

**Environment variables and `.env`.** Passwords must not be written into code (code gets
shared; passwords must not). So the code says "read the password from the environment", and
your `.env` file is where those values live on your machine only. This is why every terminal
session starts by loading `.env` — the services refuse to start without it, *on purpose*.

**Database per service.** Each service has its own database, and the databases cannot see each
other. You proved this yourself in pgAdmin: `SELECT * FROM products` inside the order database
fails — the table simply is not there. This forces the services to talk through their APIs,
which is the entire discipline of microservices.

**A token (JWT).** After you log in, user-service hands you a **signed pass** — like a concert
wristband with a tamper-proof seal. Every later request shows the wristband. Any service can
check the seal on its own, instantly, without phoning the front gate. Two facts to remember:
anyone can *read* what is on the wristband (never put secrets on it), and nobody can *alter*
it without breaking the seal. It expires after 15 minutes, because a wristband, once handed
out, cannot be taken back.

## 4. Setting up a machine from zero

What to install, in order, on a fresh Windows machine. Each has a normal installer — accept
the defaults unless noted.

1. **Docker Desktop** — docker.com. Needs a restart. Afterwards, the whale near the clock.
2. **Java (JDK 21 or newer)** — adoptium.net (choose "Temurin"). Tick the option to set
   JAVA_HOME if offered.
3. **Maven** — maven.apache.org, or easier: it comes bundled inside many JDK installers'
   ecosystems; verify with `mvn -version` in a fresh terminal.
4. **VS Code** — code.visualstudio.com. On first opening the project it will suggest the
   "Extension Pack for Java" — accept.
5. **pgAdmin** — pgadmin.org, or it may already exist if PostgreSQL was ever installed.

Check everything from a fresh terminal:

```powershell
docker --version
java -version
mvn -version
```

Each should print a version number. "not recognized" means that tool is not installed or the
terminal was open before the install finished — open a new terminal first, then reinstall.

Then the project itself: place the `ecommerce-platform` folder somewhere sensible, copy
`.env.example` to `.env`, and replace every `change-me` with values you invent. The database
passwords can be anything; `JWT_SECRET` must be at least 32 characters long.

**One Windows trap:** if the folder came from a zip, check you are not one level too shallow.
The folder containing `pom.xml` and `docker-compose.yml` is the real project. Open *that* one
in VS Code — opening its parent causes strange errors where `${USER_DB_USERNAME}` is sent to
the database as literal text.

## 5. Starting your day

The routine, every session, in order.

**Step 1 — Docker Desktop.** Start menu → Docker Desktop → wait for the whale.

**Step 2 — the database.**

```powershell
docker compose up -d postgres
```

`-d` means "in the background". Check it:

```powershell
docker compose ps
```

Wait for `Up (healthy)` — not just `Up`.

**Step 3 — the three services.** Three terminals (the `+` in VS Code's terminal panel), and in
**each one**, first load the environment, then start one service:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
    [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
  }
}
mvn -pl user-service spring-boot:run
```

(Then `product-service` in terminal two, `order-service` in terminal three.) The loading block
repeats in each because environment variables do not carry between terminals.

A service is ready when its log says `Tomcat started on port 808x`. The terminal then *stays
busy* — that is correct; the program is running in it. Closing the terminal stops the service.

**Step 4 — prove it all works.**

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\verify-phase2.ps1
```

Green PASS lines all the way down means the platform works end to end.

**Ending the day:** just close things, or `docker compose down` to stop the database.
**Never add `-v` to that command** — the `-v` deletes the data volumes, meaning every
customer, product and order.

## 6. Doing things: a guided tour

All through Swagger, no code involved.

**Browse the catalogue (no login needed):** open `http://localhost:8082/swagger-ui.html`,
find `GET /api/v1/products`, Try it out, Execute. Browsing a shop never requires an account.

**Register:** at 8081, `POST /api/v1/customers` with your name, an email, and a password of
8+ characters.

**Log in:** at 8081, `POST /api/v1/auth/login` with that email and password. Copy the long
`accessToken` string from the response.

**Show your wristband:** click the **Authorize** button at the top of the Swagger page, paste
the token, Authorize, Close. From now on every request from this page carries it.

**Place an order:** at 8083, `POST /api/v1/orders` — your customer id, an address id (create
one at 8081 first), and a list of product ids with quantities. Note what you do *not* send:
prices. The backend looks prices up itself, precisely so that nobody can name their own.

**See the data:** in pgAdmin, the `orders` and `order_items` tables now have your rows in
them — including the product name and price *copied onto* the order, so the receipt stays
true even if the catalogue changes tomorrow.

## 7. When things go wrong — real errors, decoded

Every one of these actually happened during this project's build.

| The message | What it really means | The fix |
|---|---|---|
| `port is already allocated` / `address already in use` | Two programs want the same numbered door. Usually an old PostgreSQL installed on Windows holding 5432 | Stop the Windows service `postgresql-x64-…` in Services, set it to Manual |
| `password authentication failed for user "x"` | The database role's password and your `.env` disagree. The roles were created ONCE, with whatever `.env` said then; editing `.env` later changes only one side | `docker exec -it ecommerce-postgres psql -U postgres -c "ALTER ROLE x WITH PASSWORD 'the-env-value'"` |
| `password authentication failed for user "${USER_DB_USERNAME}"` | The placeholder itself was sent — the environment was never loaded. Almost always: VS Code has the wrong folder open, or the loading block was not run in this terminal | Open the folder that contains `pom.xml`; run the `Get-Content .env` block in every new terminal |
| `Connection to localhost:5432 refused` | Nothing is listening at that door at all — Postgres is not running | Start Docker Desktop, then `docker compose up -d postgres` |
| `running scripts is disabled on this system` | Windows blocks downloaded scripts by default | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` (this window only) |
| `401 Authentication required` | "I don't know who you are." No token, an expired token (15 minutes!), or a mangled one | Log in again, press Authorize, retry |
| `403 Access denied` | "I know exactly who you are — and you still may not." A customer reading someone else's record, or doing staff-only things | Use the right account; ADMIN is granted only by a direct database UPDATE |
| `409 Conflict` | The request collides with the current state: cancelling a paid order, editing a stale copy | Re-read the record and decide again |
| `422 Unprocessable` | The request was well-formed but references something unusable — e.g. a discontinued product | Pick a different product |
| Whole screen of red Java text | Do not read it top to bottom. Find the LAST line beginning `Caused by:` — that is the actual problem; everything above is consequences | Read that one line, then act on it |

The general skill: **error messages are trying to help.** The reflex to build is reading the
message before assuming, and being suspicious when a message contradicts something you can
verify — you once had a script insist a service was down while `netstat` showed it running,
and the script was the thing that was broken.

## 8. Glossary

| Term | Plain meaning |
|---|---|
| API | The fixed list of questions a program answers, and the shape of the answers |
| Backend | The part of an app users never see; where the logic and data live |
| Container | A sealed box a program runs in, identical on every machine |
| Endpoint | One specific question an API answers, e.g. `GET /products` |
| Flyway | The tool that changes database structure in numbered, recorded steps |
| JSON | The `{ "key": "value" }` text format requests and answers travel in |
| JWT / token | The signed, expiring pass you carry after logging in |
| localhost | "This computer" |
| Microservice | A small program with one job and its own data |
| Migration | One numbered, permanent change to a database's structure |
| Port | A numbered door on a computer; each listening program takes one |
| REST | The convention of using GET/POST/PUT/DELETE against URLs |
| Role | What kind of user you are: CUSTOMER or ADMIN |
| Swagger | A service's interactive instruction manual, in the browser |
| Terminal | Operating the computer by typing commands |
| Volume | Docker's permanent storage — where the database's files really live |
| 401 vs 403 | "Who are you?" vs "You, specifically, may not" |
