"""Generates ledger-architecture.drawio: C4 views (context, containers, components, dynamic, deployment)
plus two supplementary views (bounded contexts, platform contract)."""
from xml.sax.saxutils import escape

WHITE = "fontColor=#ffffff;"
S = {
    # C4 elements
    "person": "rounded=1;whiteSpace=wrap;html=1;arcSize=40;fillColor=#08427B;strokeColor=#073B6F;" + WHITE,
    "system": "rounded=1;whiteSpace=wrap;html=1;arcSize=8;fillColor=#1168BD;strokeColor=#0B4884;" + WHITE,
    "ext": "rounded=1;whiteSpace=wrap;html=1;arcSize=8;fillColor=#8C8496;strokeColor=#736782;" + WHITE,
    "container": "rounded=1;whiteSpace=wrap;html=1;arcSize=8;fillColor=#438DD5;strokeColor=#3C7FC0;" + WHITE,
    "db": "shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;size=12;fillColor=#438DD5;strokeColor=#3C7FC0;" + WHITE,
    "queue": "shape=cylinder3;whiteSpace=wrap;html=1;boundedLbl=1;size=12;fillColor=#438DD5;strokeColor=#3C7FC0;" + WHITE,
    "component": "rounded=1;whiteSpace=wrap;html=1;arcSize=8;fillColor=#85BBF0;strokeColor=#78A8D8;fontColor=#000000;",
    "boundary": "rounded=1;whiteSpace=wrap;html=1;arcSize=2;fillColor=none;dashed=1;dashPattern=8 4;strokeColor=#666666;"
                "fontColor=#333333;verticalAlign=bottom;align=left;spacingLeft=10;spacingBottom=4;fontStyle=1;",
    "node": "rounded=0;whiteSpace=wrap;html=1;fillColor=none;strokeColor=#666666;verticalAlign=top;align=left;spacingLeft=8;fontColor=#333333;",
    # supplementary and helpers
    "box": "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;",
    "graybox": "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;",
    "green": "rounded=1;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;",
    "yellow": "rounded=1;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;",
    "core": "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;strokeWidth=2;",
    "note": "shape=note;whiteSpace=wrap;html=1;size=14;fillColor=#fffbe6;strokeColor=#d6b656;align=left;spacingLeft=6;fontSize=11;",
    "title": "text;html=1;fontSize=18;fontStyle=1;align=left;verticalAlign=middle;",
    "edge_label": "text;html=1;fontSize=10;fontColor=#404040;align=right;verticalAlign=middle;",
    "subtitle": "text;html=1;fontSize=12;align=left;verticalAlign=middle;fontColor=#555555;",
    "lifeline_head": "rounded=1;whiteSpace=wrap;html=1;arcSize=8;fillColor=#438DD5;strokeColor=#3C7FC0;fontSize=11;" + WHITE,
    "lifeline_head_ext": "rounded=1;whiteSpace=wrap;html=1;arcSize=8;fillColor=#8C8496;strokeColor=#736782;fontSize=11;" + WHITE,
    "lifeline_head_person": "rounded=1;whiteSpace=wrap;html=1;arcSize=40;fillColor=#08427B;strokeColor=#073B6F;fontSize=11;" + WHITE,
    "self": "text;html=1;align=left;verticalAlign=middle;fontSize=10;fillColor=#ffffff;strokeColor=#bbbbbb;rounded=1;spacingLeft=4;",
    # edges
    "rel": "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;strokeColor=#707070;strokeWidth=1.5;endArrow=blockThin;endFill=1;"
           "fontSize=10;fontColor=#404040;labelBackgroundColor=#ffffff;",
    "rel_async": "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;strokeColor=#707070;strokeWidth=1.5;dashed=1;endArrow=blockThin;"
                 "endFill=1;fontSize=10;fontColor=#404040;labelBackgroundColor=#ffffff;",
    "msg": "html=1;strokeColor=#3C7FC0;strokeWidth=1.5;endArrow=blockThin;endFill=1;fontSize=10;fontColor=#222222;"
           "labelBackgroundColor=#ffffff;verticalAlign=bottom;",
    "msg_ret": "html=1;strokeColor=#707070;strokeWidth=1;dashed=1;endArrow=open;fontSize=10;fontColor=#222222;"
               "labelBackgroundColor=#ffffff;verticalAlign=bottom;",
    "msg_err": "html=1;strokeColor=#b85450;strokeWidth=1.5;dashed=1;endArrow=open;fontSize=10;fontColor=#b85450;"
               "labelBackgroundColor=#ffffff;verticalAlign=bottom;",
    "lifeline": "html=1;strokeColor=#9a9a9a;dashed=1;endArrow=none;",
    "edge_gray": "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;strokeColor=#888888;strokeWidth=1;dashed=1;endArrow=open;"
                 "fontSize=10;labelBackgroundColor=#ffffff;",
}


def lbl(name, kind, desc=""):
    out = f"<b>{name}</b><br><span style=\"font-size:10px\">[{kind}]</span>"
    return out + (f"<br><br><span style=\"font-size:11px\">{desc}</span>" if desc else "")


class Page:
    def __init__(self, name):
        self.name, self.cells, self.n = name, [], 0

    def node(self, label, x, y, w, h, style, parent="1"):
        self.n += 1
        cid = f"n{self.n}"
        self.cells.append(
            f'<mxCell id="{cid}" value="{escape(label, {chr(34): "&quot;"})}" style="{S[style]}" vertex="1" parent="{parent}">'
            f'<mxGeometry x="{x}" y="{y}" width="{w}" height="{h}" as="geometry"/></mxCell>'
        )
        return cid

    def edge(self, src, dst, label="", style="rel", extra="", points=()):
        self.n += 1
        pts = ""
        if points:
            pts = '<Array as="points">' + "".join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in points) + "</Array>"
        self.cells.append(
            f'<mxCell id="e{self.n}" value="{escape(label, {chr(34): "&quot;"})}" style="{S[style]}{extra}" edge="1" parent="1" source="{src}" target="{dst}">'
            f'<mxGeometry relative="1" as="geometry">{pts}</mxGeometry></mxCell>'
        )

    def line(self, x1, y1, x2, y2, label="", style="msg"):
        self.n += 1
        self.cells.append(
            f'<mxCell id="l{self.n}" value="{escape(label, {chr(34): "&quot;"})}" style="{S[style]}" edge="1" parent="1">'
            f'<mxGeometry relative="1" as="geometry"><mxPoint x="{x1}" y="{y1}" as="sourcePoint"/>'
            f'<mxPoint x="{x2}" y="{y2}" as="targetPoint"/></mxGeometry></mxCell>'
        )

    def legend(self, x, y, items):
        self.node("<b>Legend</b>", x, y, 200, 24, "subtitle")
        cy = y + 28
        for text, style in items:
            h = 40 if style in ("db", "queue") else 28
            self.node(text, x, cy, 200, h, style)
            cy += h + 6

    def xml(self, i):
        body = "".join(self.cells)
        return (
            f'<diagram id="p{i}" name="{escape(self.name)}"><mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" '
            f'page="1" pageWidth="1600" pageHeight="1000"><root><mxCell id="0"/><mxCell id="1" parent="0"/>{body}</root></mxGraphModel></diagram>'
        )


def sequence(p, parts, msgs, x0=40, y0=90, gap=230, top_h=60, step=46):
    """parts: [(key, label, style)]; msgs: [(src, dst, text, style)], or (src, None, text, 'self') for a note."""
    xs = {}
    for i, (key, label, style) in enumerate(parts):
        x = x0 + i * gap
        p.node(label, x, y0, gap - 30, top_h, style)
        xs[key] = x + (gap - 30) / 2
    y = y0 + top_h + 30
    for src, dst, text, style in msgs:
        if style == "self":
            p.node(text, xs[src] + 8, y - 14, gap * 1.6, 30, "self")
        else:
            p.line(xs[src], y, xs[dst], y, text, style)
        y += step
    end = y + 10
    for key in xs:
        p.line(xs[key], y0 + top_h, xs[key], end, "", "lifeline")
    return end


pages = []

# 1. C4 L1 — Context
p = Page("1. C4 · Context")
p.node("Level 1 — System context", 40, 20, 900, 36, "title")
p.node("Who uses the Ledger platform and which external systems it interacts with", 40, 52, 900, 24, "subtitle")
team = p.node(lbl("Fintech team", "Person", "A tenant's finance staff, approvers and developers"), 80, 110, 240, 120, "person")
admin = p.node(lbl("Platform admin", "Person", "Tenant onboarding and global configuration; no access to tenant data except via break-glass"), 80, 330, 240, 130, "person")
led = p.node(lbl("Ledger platform", "Software system",
                 "Multi-tenant wallets in COP, extensible to other currencies: direct and two-phase transfers, "
                 "fees, limits, daily close, reconciliation against the bank, web console and signed webhooks"),
             560, 210, 380, 200, "system")
fin = p.node(lbl("Fintech system", "External software system", "Moves its customers' money and receives notifications"), 560, 560, 380, 120, "ext")
bank = p.node(lbl("Bank", "External software system", "Drops the daily bank statement of the settlement account"), 1160, 230, 260, 130, "ext")
p.edge(team, led, "Reconciles, approves adjustments,<br>registers OAuth2 clients<br>[HTTPS, passkeys]", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.2;")
p.edge(admin, led, "Onboards tenants<br>and configures<br>[HTTPS, passkeys]", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.75;")
p.edge(fin, led, "Moves money and queries balances<br>[REST /v1, FAPI 2.0 + DPoP]", "rel", "exitX=0.3;exitY=0;entryX=0.3;entryY=1;")
p.edge(led, fin, "Notifies status changes<br>[JWS-signed webhooks]", "rel_async", "exitX=0.7;exitY=1;entryX=0.7;entryY=0;")
p.edge(bank, led, "Delivers the daily bank statement<br>[CSV in object storage]", "rel", "exitX=0;exitY=0.5;entryX=1;entryY=0.4;")
p.node("<b>Business invariants</b><br>• Every journal entry balances per currency: Σ debits = Σ credits<br>"
       "• A posted journal entry is immutable; it is corrected with a reversing entry<br>"
       "• The same Idempotency-Key never moves money twice<br>"
       "• A wallet never goes below its policy (minimum balance or overdraft)<br>"
       "• A tenant never sees another tenant's data<br>"
       "• Manual adjustments and policy changes: dual approval",
       1060, 470, 460, 130, "note")
p.legend(1300, 700, [("Person", "person"), ("Our system", "system"), ("External system", "ext")])
pages.append(p)

# 2. C4 L2 — Containers, phase 1
p = Page("2. C4 · Containers (phase 1)")
p.node("Level 2 — Containers, phase 1", 40, 20, 900, 36, "title")
p.node("What is deployed separately, with which technology, and how it communicates", 40, 52, 900, 24, "subtitle")
person = p.node(lbl("Fintech team / Admin", "Person"), 40, 120, 200, 80, "person")
fin = p.node(lbl("Fintech system", "External system"), 1580, 120, 200, 80, "ext")
bank = p.node(lbl("Bank", "External system"), 1580, 800, 200, 80, "ext")
p.node("Ledger platform [Software system]", 290, 90, 1250, 900, "boundary")
spa = p.node(lbl("Web console", "Container: React + Vite, served from a CDN", "Finance, Administration and Developers areas; never receives tokens"), 320, 110, 260, 120, "container")
bff = p.node(lbl("console-bff", "Container: Spring Boot 4, Gateway Server MVC", "OIDC login, session, CSRF, CSP; forwards to the APIs with opaque token + DPoP"), 320, 330, 260, 130, "container")
sess = p.node(lbl("Sessions", "PostgreSQL"), 340, 560, 220, 80, "db")
auth = p.node(lbl("auth-server", "Container: Spring Security 7 Authorization Server", "Passkeys, PAR, PKCE, private_key_jwt, opaque DPoP tokens, introspection, client registration"), 740, 110, 300, 130, "container")
adb = p.node(lbl("Identities", "PostgreSQL", "users, clients, WebAuthn"), 1120, 120, 220, 100, "db")
api = p.node(lbl("ledger-app · api", "Container: Spring Boot 4, Spring Modulith", "ledger, approvals, audit and reconciliation modules; REST API /v1"), 740, 330, 300, 130, "container")
batch = p.node(lbl("ledger-app · batch", "Container: Spring Batch, scheduled task", "Daily close per tenant and reconciliation"), 740, 800, 300, 110, "container")
ldb = p.node(lbl("Ledger", "PostgreSQL + RLS", "accounts, journal entries, audit log, outbox, BATCH_*"), 760, 560, 260, 110, "db")
topic = p.node(lbl("ledger-events.fifo", "SNS FIFO"), 1120, 360, 200, 80, "queue")
q = p.node(lbl("webhooks.fifo + DLQ", "SQS FIFO"), 1120, 520, 200, 80, "queue")
wh = p.node(lbl("webhooks", "Container: Spring Boot 4", "Subscriptions, JWS-signed deliveries, retries"), 1360, 330, 160, 140, "container")
whdb = p.node(lbl("Webhooks", "PostgreSQL"), 1360, 620, 160, 80, "db")
store = p.node(lbl("Object storage", "S3", "bank statements, audit anchors (WORM)"), 1120, 810, 240, 100, "db")
p.edge(person, spa, "Uses<br>[HTTPS]", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
p.edge(spa, bff, "Calls /bff/*<br>[JSON/HTTPS, session cookie]")
p.edge(bff, auth, "OIDC login with PAR + PKCE,<br>requests DPoP tokens [HTTPS]", "rel", "exitX=1;exitY=0.2;entryX=0;entryY=0.7;")
p.edge(bff, api, "Forwards with<br>opaque token + DPoP<br>[JSON/HTTPS]", "rel", "exitX=1;exitY=0.6;entryX=0;entryY=0.6;")
p.edge(bff, sess, "Stores the session<br>[JDBC]")
p.edge(auth, adb, "[JDBC]", "rel", "exitX=1;exitY=0.3;entryX=0;entryY=0.5;")
p.edge(api, auth, "Introspection<br>[HTTPS]", "rel", "exitX=0.5;exitY=0;entryX=0.5;entryY=1;")
p.edge(api, ldb, "Reads and writes<br>[JDBC, SET LOCAL tenant]")
p.edge(batch, ldb, "Closes the day, reconciles<br>[JDBC]", "rel", "exitX=0.5;exitY=0;entryX=0.5;entryY=1;")
p.edge(batch, store, "Reads statements,<br>writes anchors [HTTPS]", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
p.edge(api, topic, "Publishes v1 events<br>[outbox, HTTPS]", "rel_async", "exitX=1;exitY=0.4;entryX=0;entryY=0.5;")
p.edge(topic, q, "Fans out<br>[subscription]", "rel_async", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.edge(q, wh, "Consumes<br>[HTTPS]", "rel_async", "exitX=1;exitY=0.5;entryX=0;entryY=0.75;", points=[(1340, 560), (1340, 435)])
p.edge(wh, whdb, "", "rel", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.edge(wh, auth, "Introspection [HTTPS]", "rel", "exitX=0.5;exitY=0;entryX=1;entryY=0.8;", points=[(1440, 260)])
p.edge(fin, auth, "Requests tokens: client_credentials, private_key_jwt + DPoP [HTTPS]", "rel", "exitX=0.5;exitY=0;entryX=0.5;entryY=0;", points=[(1680, 100), (890, 100)])
p.edge(fin, api, "Moves money<br>[REST /v1 + DPoP]", "rel", "exitX=0.5;exitY=1;entryX=1;entryY=0.2;", points=[(1680, 290), (1060, 290)])
p.edge(wh, fin, "Signed webhook<br>[HTTPS, JWS]", "rel_async", "exitX=1;exitY=0.3;entryX=0.3;entryY=1;")
p.edge(bank, store, "Drops the daily CSV", "rel", "exitX=0;exitY=0.5;entryX=1;entryY=0.5;")
p.legend(40, 700, [("Person", "person"), ("Container", "container"), ("Database / queue", "db"), ("External system", "ext")])
p.node("Solid line: synchronous<br>Dashed: asynchronous", 40, 885, 230, 40, "subtitle")
pages.append(p)

# 3. C4 L2 — Containers, phase 2
p = Page("3. C4 · Containers (phase 2)")
p.node("Level 2 — Containers, phase 2: reconciliation as its own service", 40, 20, 1100, 36, "title")
p.node("Only the highlighted parts change; console, BFF and auth-server stay as in phase 1", 40, 52, 900, 24, "subtitle")
p.node("Ledger platform [Software system]", 40, 90, 1300, 640, "boundary")
api = p.node(lbl("ledger-app · api", "Container: Spring Boot 4, Spring Modulith", "ledger, approvals and audit modules; journal feed API by sequence"), 80, 140, 300, 130, "container")
ldb = p.node(lbl("Ledger", "PostgreSQL + RLS"), 100, 360, 260, 90, "db")
batch = p.node(lbl("ledger-app · batch", "Spring Batch", "Daily close"), 80, 530, 300, 100, "container")
topic = p.node(lbl("ledger-events.fifo", "SNS FIFO"), 480, 160, 200, 80, "queue")
q1 = p.node(lbl("webhooks.fifo + DLQ", "SQS FIFO"), 760, 120, 220, 80, "queue")
q2 = p.node(lbl("reconciliation.fifo + DLQ", "SQS FIFO · new subscription"), 760, 300, 220, 90, "queue")
wh = p.node(lbl("webhooks", "Container: Spring Boot 4"), 1060, 120, 220, 80, "container")
rec = p.node(lbl("reconciliation", "New container: Spring Boot 4 + Spring Batch",
                 "Idempotent consumer; nightly job against the bank statement; resource server with opaque token + DPoP"), 1040, 280, 260, 150, "system")
rdb = p.node(lbl("Reconciliation", "PostgreSQL · own database", "journal entry projection, results, BATCH_*"), 1050, 500, 240, 110, "db")
store = p.node(lbl("Object storage", "S3", "bank statements"), 760, 520, 200, 90, "db")
p.edge(api, ldb, "[JDBC]")
p.edge(batch, ldb, "[JDBC]", "rel", "exitX=0.5;exitY=0;entryX=0.5;entryY=1;")
p.edge(api, topic, "Publishes v1 events<br>[outbox]", "rel_async", "exitX=1;exitY=0.4;entryX=0;entryY=0.5;")
p.edge(topic, q1, "", "rel_async", "exitX=1;exitY=0.3;entryX=0;entryY=0.5;")
p.edge(topic, q2, "New subscription:<br>the producer does not change", "rel_async", "exitX=1;exitY=0.7;entryX=0;entryY=0.5;")
p.edge(q1, wh, "Consumes", "rel_async")
p.edge(q2, rec, "Consumes", "rel_async")
p.edge(rec, rdb, "[JDBC]")
p.edge(rec, store, "Reads statements", "rel", "exitX=0;exitY=0.85;entryX=1;entryY=0.5;")
p.edge(rec, api, "Rebuilds the projection (journal feed API)<br>and proposes adjustments [REST /v1 + DPoP]", "rel", "exitX=1;exitY=0.8;entryX=0;entryY=0.5;",
       points=[(1320, 400), (1320, 690), (60, 690), (60, 205)])
p.node("<b>Rules</b><br>• Each service owns its database: reconciliation has no permissions on the ledger database<br>"
       "• The ledger remains a single service: debit and credit in one ACID transaction",
       40, 760, 700, 60, "note")
p.legend(1380, 120, [("Container", "container"), ("New container", "system"), ("Database / queue", "db")])
pages.append(p)

# 4. C4 L3 — ledger-app components
p = Page("4. C4 · Components ledger-app")
p.node("Level 3 — ledger-app components (api profile)", 40, 20, 1100, 36, "title")
p.node("Spring Modulith: one module per bounded context, hexagonal inside each module", 40, 52, 1100, 24, "subtitle")
bff = p.node(lbl("console-bff", "Container"), 60, 100, 200, 70, "container")
fin = p.node(lbl("Fintech system", "External system"), 300, 100, 200, 70, "ext")
auth = p.node(lbl("auth-server", "Container"), 1260, 100, 200, 70, "container")
p.node("ledger-app [Container: Spring Boot 4 + Spring Modulith]", 20, 200, 1500, 660, "boundary")
sec = p.node(lbl("Security filter chain", "Spring Security, resource server",
                 "Introspection with short cache, DPoP proof, scopes and roles, step-up by amount, sets the tenant, Bucket4j per client"),
             60, 220, 980, 80, "component")
web = p.node(lbl("REST API /v1", "@RestController on interfaces generated from OpenAPI",
                 "Transfers, accounts, journal feed by sequence; Idempotency-Key, ProblemDetail, cursor"), 60, 330, 980, 100, "component")
cmd = p.node(lbl("Commands", "Spring beans, @Transactional", "CreateTransfer, PostTransfer, VoidTransfer, ReverseEntry, OpenAccount"), 60, 470, 280, 100, "component")
qry = p.node(lbl("Queries", "Query ports + JdbcClient", "AccountStatementQuery, JournalFeedQuery → DTOs"), 420, 470, 280, 100, "component")
idem = p.node(lbl("Idempotency", "Component + table", "Key, body hash and response"), 760, 470, 280, 100, "component")
dom = p.node(lbl("Ledger domain", "Plain Java", "Account, Transfer, JournalEntry, BusinessDay, FeeSchedule, AccountPolicy, Money"), 60, 610, 280, 100, "component")
jpa = p.node(lbl("Persistence", "Spring Data JPA (adapter)", "Wallets with FOR UPDATE by id; system accounts without lock"), 420, 610, 280, 100, "component")
exp = p.node(lbl("Hold expiration", "@Scheduled + ShedLock", "Moves expired holds to EXPIRED"), 760, 610, 280, 100, "component")
outb = p.node(lbl("Translator and externalizer", "Modulith outbox + Spring Cloud AWS (SNS)", "→ JournalEntryPostedV1, TransferStatusChangedV1"), 60, 750, 280, 90, "component")
recm = p.node(lbl("reconciliation", "Modulith module", "In-process event projection; discrepancy → adjustment proposal"), 1100, 330, 300, 100, "component")
appr = p.node(lbl("approvals", "Modulith module", "Dual approval: adjustments, policies, fees, break-glass"), 1100, 470, 300, 100, "component")
audit = p.node(lbl("audit", "Modulith module", "Written in the transaction; sealer every minute with ShedLock"), 1100, 610, 300, 100, "component")
topic = p.node(lbl("ledger-events.fifo", "SNS FIFO"), 80, 900, 240, 80, "queue")
ldb = p.node(lbl("Ledger", "PostgreSQL + RLS"), 440, 900, 240, 80, "db")
p.edge(bff, sec, "[JSON/HTTPS + DPoP]", "rel", "exitX=0.5;exitY=1;entryX=0.1;entryY=0;")
p.edge(fin, sec, "[REST /v1 + DPoP]", "rel", "exitX=0.5;exitY=1;entryX=0.4;entryY=0;")
p.edge(sec, auth, "Introspection [HTTPS]", "rel", "exitX=1;exitY=0.3;entryX=0.5;entryY=1;")
p.edge(sec, web, "")
p.edge(web, cmd, "", "rel", "exitX=0.1;exitY=1;entryX=0.3;entryY=0;")
p.edge(web, qry, "", "rel", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.edge(cmd, idem, "Stores the key in the same transaction", "rel", "exitX=0.9;exitY=0;entryX=0.5;entryY=0;", points=[(312, 450), (900, 450)])
p.edge(cmd, dom, "")
p.edge(jpa, dom, "", "edge_gray", "exitX=0;exitY=0.5;entryX=1;entryY=0.5;")
p.edge(cmd, audit, "Logs in the same transaction", "rel", "exitX=1;exitY=0.95;entryX=0;entryY=0.5;", points=[(380, 565), (380, 590), (1070, 590), (1070, 660)])
p.edge(appr, cmd, "Approved adjustment", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;", points=[(1430, 520), (1430, 730), (40, 730), (40, 520)])
p.edge(recm, appr, "Proposes adjustment")
p.edge(dom, outb, "Domain events", "rel_async")
p.edge(outb, recm, "In-process event", "rel_async", "exitX=1;exitY=0.5;entryX=1;entryY=0.5;", points=[(1470, 795), (1470, 380)])
p.edge(outb, topic, "Publishes after commit", "rel_async")
p.edge(jpa, ldb, "[JDBC, SET LOCAL tenant]")
p.legend(1260, 880, [("Container", "container"), ("Component", "component"), ("Database / queue", "db")])
p.node("Dashed gray: implements the port", 960, 900, 260, 30, "subtitle")
pages.append(p)

# 5. C4 L3 — console-bff components
p = Page("5. C4 · Components console-bff")
p.node("Level 3 — console-bff components", 40, 20, 1100, 36, "title")
p.node("Spring Cloud Gateway Server MVC + Spring Security; the DPoP relay is custom code", 40, 52, 1100, 24, "subtitle")
spa = p.node(lbl("Web console", "Container: React"), 60, 120, 220, 80, "container")
p.node("console-bff [Container: Spring Boot 4]", 360, 90, 960, 640, "boundary")
chain = p.node(lbl("Security filter chain", "Spring Security", "Mandatory session, CSRF (cookie + header), strict CSP; ends the session on an API 401"),
               380, 110, 280, 120, "component")
routes = p.node(lbl("Routes", "Gateway Server MVC", "/bff/api/** → ledger-app<br>/bff/webhooks/** → webhooks"), 380, 270, 280, 110, "component")
relay = p.node(lbl("DPoP relay", "Custom HandlerFilterFunction", "Authorization: DPoP + per-request proof (htm, htu, ath)"), 380, 420, 280, 110, "component")
login = p.node(lbl("OIDC login", "oauth2Login", "PAR + PKCE, private_key_jwt; user data from the ID token"), 1020, 110, 280, 120, "component")
tok = p.node(lbl("DPoP token client", "RestClient token client + setHeadersConverter", "Adds the DPoP proof when requesting and refreshing tokens"), 1020, 270, 280, 110, "component")
mgr = p.node(lbl("Authorized client manager", "OAuth2AuthorizedClientManager", "Returns the session's current token and refreshes it"), 700, 420, 280, 110, "component")
sess = p.node(lbl("Session", "Spring Session JDBC", "Stores the user's tokens; 15 min idle, 8 h maximum"), 700, 590, 280, 100, "component")
keys = p.node(lbl("Key provider", "libs/dpop", "DPoP and private_key_jwt keys"), 1020, 590, 280, 100, "component")
auth = p.node(lbl("auth-server", "Container"), 1380, 180, 200, 80, "container")
api = p.node(lbl("ledger-app · api", "Container"), 300, 790, 180, 70, "container")
whs = p.node(lbl("webhooks", "Container"), 510, 790, 180, 70, "container")
sdb = p.node(lbl("Sessions", "PostgreSQL"), 740, 780, 200, 80, "db")
sm = p.node(lbl("Secrets Manager", "Cloud service"), 1060, 790, 200, 70, "ext")
p.edge(spa, chain, "/bff/* [cookie + CSRF]", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.4;")
p.edge(chain, login, "No session", "rel", "exitX=1;exitY=0.4;entryX=0;entryY=0.4;")
p.edge(chain, routes, "With session")
p.edge(routes, relay, "")
p.edge(login, tok, "Exchanges the code")
p.edge(login, auth, "PAR, authorize,<br>callback [HTTPS]", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.3;")
p.edge(tok, auth, "/token with DPoP", "rel", "exitX=1;exitY=0.4;entryX=0;entryY=0.8;")
p.edge(relay, mgr, "Gets<br>token")
p.edge(mgr, tok, "Refreshes", "rel", "exitX=1;exitY=0.5;entryX=0.7;entryY=1;", points=[(1216, 475)])
p.edge(tok, keys, "", "rel", "exitX=0.3;exitY=1;entryX=0.3;entryY=0;")
p.edge(relay, keys, "Signs the proof", "rel", "exitX=1;exitY=0.9;entryX=0.6;entryY=0;", points=[(690, 519), (690, 560), (1188, 560)])
p.edge(mgr, sess, "")
p.edge(sess, sdb, "[JDBC]")
p.edge(relay, api, "[JSON/HTTPS + DPoP]", "rel", "exitX=0.2;exitY=1;entryX=0.7;entryY=0;")
p.edge(relay, whs, "[JSON/HTTPS + DPoP]", "rel", "exitX=0.7;exitY=1;entryX=0.5;entryY=0;")
p.edge(keys, sm, "Reads keys [HTTPS]")
p.legend(60, 300, [("Container", "container"), ("Component", "component"), ("Database", "db"), ("External", "ext")])
pages.append(p)

# 6. Dynamic — login, passkey and step-up
p = Page("6. Dynamic · login and step-up")
p.node("Dynamic — passkey login and above-threshold transfer with step-up", 40, 20, 1300, 36, "title")
parts = [("u", lbl("Operator", "Person"), "lifeline_head_person"), ("spa", lbl("Web console", "React"), "lifeline_head"),
         ("bff", lbl("console-bff", "Spring Boot"), "lifeline_head"), ("auth", lbl("auth-server", "Spring Security 7"), "lifeline_head"),
         ("api", lbl("ledger-app · api", "Spring Boot"), "lifeline_head")]
msgs = [
    ("u", "spa", "1. Opens the console", "msg"),
    ("spa", "bff", "2. GET /bff/me (no session)", "msg"),
    ("bff", "spa", "3. 401 → redirects to /oauth2/authorization/ledger", "msg_ret"),
    ("bff", "auth", "4. PAR: POST /oauth2/par (private_key_jwt, code_challenge)", "msg"),
    ("auth", "bff", "5. request_uri", "msg_ret"),
    ("u", "auth", "6. /authorize?request_uri=… → signs in with passkey (WebAuthn)", "msg"),
    ("auth", "bff", "7. Redirects to /login/oauth2/code/ledger with the code", "msg_ret"),
    ("bff", "auth", "8. POST /oauth2/token: code + code_verifier + DPoP proof", "msg"),
    ("auth", "bff", "9. Opaque token (token_type=DPoP) + ID token", "msg_ret"),
    ("bff", None, "10. Creates the JDBC session; cookie HttpOnly · Secure · SameSite=Strict", "self"),
    ("spa", "bff", "11. POST /bff/api/v1/transfers (cookie + CSRF), amount above threshold", "msg"),
    ("bff", "api", "12. POST /v1/transfers · Authorization: DPoP · proof with ath", "msg"),
    ("api", "auth", "13. Introspection → active, cnf.jkt, tenant_id, acr, auth_time", "msg"),
    ("api", None, "14. Valid DPoP proof; auth_time older than the threshold's max_age", "self"),
    ("api", "bff", "15. 401 insufficient_user_authentication (RFC 9470) with max_age", "msg_err"),
    ("bff", "spa", "16. Step-up required", "msg_ret"),
    ("u", "auth", "17. Re-authentication with passkey (steps 4-9 with max_age=0)", "msg"),
    ("bff", "api", "18. Retries with the new token + new DPoP proof", "msg"),
    ("api", "bff", "19. 201 Created", "msg_ret"),
]
sequence(p, parts, msgs, gap=290, step=40)
pages.append(p)

# 7. Dynamic — two-phase transfer from a fintech
p = Page("7. Dynamic · two-phase transfer")
p.node("Dynamic — two-phase transfer from the fintech (DPoP + idempotency)", 40, 20, 1300, 36, "title")
parts = [("f", lbl("Fintech system", "External"), "lifeline_head_ext"), ("auth", lbl("auth-server", "Spring Security 7"), "lifeline_head"),
         ("api", lbl("ledger-app · api", "Spring Boot"), "lifeline_head"), ("db", lbl("Ledger", "PostgreSQL + RLS"), "lifeline_head"),
         ("sns", lbl("ledger-events.fifo", "SNS FIFO"), "lifeline_head")]
msgs = [
    ("f", "auth", "1. POST /oauth2/token · client_credentials · private_key_jwt + DPoP proof", "msg"),
    ("auth", "f", "2. Opaque token, token_type=DPoP (bound to cnf.jkt)", "msg_ret"),
    ("f", "api", "3. POST /v1/transfers {mode: two_phase} · DPoP + proof · Idempotency-Key", "msg"),
    ("api", "auth", "4. Introspection (short cache) → cnf.jkt, tenant_id, scope transfers:write", "msg"),
    ("api", None, "5. Verifies htm, htu, ath and the key; Bucket4j per client", "self"),
    ("api", "db", "6. BEGIN · SET LOCAL tenant · INSERT idempotency · FOR UPDATE wallets by id", "msg"),
    ("api", "db", "7. Validates the policy · places a hold · Transfer PENDING · audit log · outbox · COMMIT", "msg"),
    ("api", "f", "8. 201 Created · Location · status PENDING", "msg_ret"),
    ("api", "sns", "9. After commit: TransferStatusChangedV1 (MessageGroupId = tenantId)", "msg"),
    ("f", "api", "10. POST /v1/transfers/{id}/post · DPoP + new proof · new Idempotency-Key", "msg"),
    ("api", "db", "11. BEGIN · releases the hold · 3-posting journal entry (source, destination, fee) · COMMIT", "msg"),
    ("api", "f", "12. 200 OK · status POSTED", "msg_ret"),
    ("api", "sns", "13. JournalEntryPostedV1 + TransferStatusChangedV1", "msg"),
    ("f", "api", "14. Retry of step 10 with the same Idempotency-Key (network timeout)", "msg"),
    ("api", "f", "15. The same stored response; money does not move again", "msg_ret"),
]
end = sequence(p, parts, msgs, gap=300, step=44)
p.node("Variants: VOID releases the hold without a journal entry · on timeout, the expiration job moves it to EXPIRED · "
       "the same key with a different body returns 422 · a Bearer with a DPoP token returns 401", 40, end + 20, 1300, 40, "note")
pages.append(p)

# 8. Dynamic — event to webhook
p = Page("8. Dynamic · event to webhook")
p.node("Dynamic — from journal entry to signed webhook (outbox, fan-out, retries, DLQ)", 40, 20, 1300, 36, "title")
parts = [("api", lbl("ledger-app · api", "Modulith outbox"), "lifeline_head"), ("sns", lbl("ledger-events.fifo", "SNS FIFO"), "lifeline_head"),
         ("q", lbl("webhooks.fifo", "SQS FIFO + DLQ"), "lifeline_head"), ("wh", lbl("webhooks", "Spring Boot"), "lifeline_head"),
         ("wdb", lbl("Webhooks", "PostgreSQL"), "lifeline_head"), ("f", lbl("Fintech endpoint", "External"), "lifeline_head_ext")]
msgs = [
    ("api", None, "1. The journal entry and its event_publication row commit in the same transaction", "self"),
    ("api", "sns", "2. Publishes JournalEntryPostedV1 (MessageGroupId = tenantId, content-based dedup)", "msg"),
    ("sns", "api", "3. Ack → Modulith marks the publication as completed", "msg_ret"),
    ("sns", "q", "4. Fan-out to each subscribed queue", "msg"),
    ("wh", "q", "5. ReceiveMessage (long polling)", "msg"),
    ("wh", "wdb", "6. BEGIN · INSERT processed eventId (if present: ack and stop) · creates the delivery · COMMIT", "msg"),
    ("wh", "f", "7. JWS-signed POST (kid published in the JWKS) · egress via fixed IP", "msg"),
    ("f", "wh", "8. 2xx", "msg_ret"),
    ("wh", "q", "9. DeleteMessage", "msg"),
    ("f", "wh", "8'. 5xx or timeout", "msg_err"),
    ("wh", "q", "9'. ChangeMessageVisibility with exponential backoff; the tenant's group waits in order", "msg"),
    ("q", None, "10'. After maxReceiveCount → DLQ; reprocessed from the console", "self"),
]
end = sequence(p, parts, msgs, gap=250, step=46)
p.node("If the app dies between the commit and step 2, Modulith resends the pending publication on restart. "
       "A duplicate reaches step 6 and has no effect.", 40, end + 20, 1300, 40, "note")
pages.append(p)

# 9. Dynamic — daily closing batch
p = Page("9. Dynamic · daily close")
p.node("Dynamic — daily close with Spring Batch (partitioned by tenant and restartable)", 40, 20, 1300, 36, "title")
parts = [("s", lbl("Scheduler", "EventBridge Scheduler"), "lifeline_head_ext"), ("b", lbl("ledger-app · batch", "Spring Batch"), "lifeline_head"),
         ("jr", lbl("JobRepository", "PostgreSQL, BATCH_*"), "lifeline_head"), ("db", lbl("Ledger", "PostgreSQL + RLS"), "lifeline_head"),
         ("s3", lbl("Audit anchors", "S3 Object Lock"), "lifeline_head")]
msgs = [
    ("s", "b", "1. 00:00 COT · RunTask profile=batch · job=dailyClose, date=D", "msg"),
    ("b", "jr", "2. JobInstance(date=D): COMPLETED → exits · FAILED → resumes", "msg"),
    ("b", None, "3. Partitioner: one partition per tenant (in parallel)", "self"),
    ("b", "db", "4. Per partition: SET LOCAL tenant · reads accounts by id in chunks", "msg"),
    ("b", "db", "5. Closing balance = previous close + postings of day D → INSERT closing_balance", "msg"),
    ("b", "jr", "6. COMMIT per chunk with the last processed id in the step context", "msg"),
    ("b", "db", "7. Verifies Σ debits = Σ credits for the day per currency · BusinessDay D → CLOSED", "msg"),
    ("b", "db", "8. Reads the head of the tenant's sealed audit log chain", "msg"),
    ("b", "s3", "9. Writes the day's anchor (compliance mode: nobody can delete or change it)", "msg"),
    ("b", "jr", "10. JobExecution COMPLETED", "msg"),
    ("b", None, "Failure at step 5: the task dies; the next run resumes from the last committed chunk", "self"),
]
sequence(p, parts, msgs, gap=290, step=46)
pages.append(p)

# 10. Deployment — AWS
p = Page("10. Deployment · AWS")
p.node("Deployment — AWS (primary deployment)", 40, 20, 900, 36, "title")
gh = p.node(lbl("GitHub Actions", "CI/CD", "mvn verify · Trivy · buildpacks · cosign keyless · OIDC"), 40, 80, 300, 90, "ext")
ecr = p.node(lbl("ECR", "Registry", "signed images"), 400, 80, 180, 95, "db")
p.edge(gh, ecr, "push")
user = p.node(lbl("Users and fintechs", "People / systems"), 40, 250, 160, 70, "person")
cfn = p.node("<b>CloudFront</b> + ACM + WAF<br><span style=\"font-size:10px\">console.* · auth.* · api.* · CSP</span>", 240, 240, 220, 90, "node")
spa = p.node(lbl("Web console", "Private S3 (OAC)"), 250, 380, 200, 70, "container")
p.edge(user, cfn, "HTTPS")
p.edge(cfn, spa, "/", "rel", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.node("<b>VPC</b> · 2 availability zones", 500, 190, 980, 560, "node")
p.node("Public subnets", 520, 220, 940, 100, "node")
alb = p.node("<b>ALB</b> HTTPS<br><span style=\"font-size:10px\">CloudFront prefix list only<br>+ secret origin header</span>", 540, 240, 260, 72, "node")
nat = p.node("<b>NAT Gateway</b> + Elastic IP<br><span style=\"font-size:10px\">fixed webhook egress</span>", 840, 245, 240, 65, "node")
p.node("<b>VPC endpoints</b><br><span style=\"font-size:10px\">S3 · ECR · Secrets · SNS · SQS · Logs</span>", 1120, 245, 320, 65, "node")
p.edge(cfn, alb, "/bff/*<br>/oauth2/*<br>/login/*<br>auth.* · api.*", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
p.node("Private subnets · ECS Fargate", 520, 340, 940, 390, "node")
bff = p.node(lbl("console-bff", "ECS Service"), 540, 400, 170, 70, "container")
auth = p.node(lbl("auth-server", "ECS Service"), 730, 400, 170, 70, "container")
api = p.node(lbl("ledger-app · api", "ECS Service"), 920, 400, 170, 70, "container")
whs = p.node(lbl("webhooks", "ECS Service"), 1110, 400, 170, 70, "container")
tasks = p.node(lbl("ledger-app · batch / migrate", "ECS RunTask"), 1300, 400, 150, 90, "container")
for svc, ex in ((bff, "0.25"), (auth, "0.5"), (api, "0.75")):
    p.edge(alb, svc, "", "rel", f"exitX={ex};exitY=1;entryX=0.5;entryY=0;")
rds = p.node(lbl("RDS PostgreSQL", "Multi-AZ · KMS · force_ssl · PITR 7 days", "databases: auth · ledger · sessions · webhooks"), 640, 590, 400, 110, "db")
for svc in (bff, auth, api, whs):
    p.edge(svc, rds, "", "edge_gray", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.edge(whs, nat, "", "edge_gray", "exitX=0.5;exitY=0;entryX=0.8;entryY=1;")
msg = p.node(lbl("SNS FIFO → SQS FIFO", "+ DLQ"), 1540, 560, 200, 80, "queue")
s3 = p.node(lbl("S3 (KMS)", "Object Lock on anchors", "statements/ · audit-anchors/"), 1540, 400, 200, 100, "db")
p.node(lbl("Secrets Manager + KMS", "rotation"), 1540, 680, 200, 70, "db")
sched = p.node(lbl("EventBridge Scheduler", "cron 00:00 COT"), 1540, 250, 200, 70, "ext")
p.node(lbl("CloudWatch", "logs · metrics · alerts · Budgets"), 1540, 90, 200, 70, "ext")
p.edge(api, msg, "publishes", "rel_async", "exitX=0.8;exitY=1;entryX=0;entryY=0.3;", points=[(1056, 540), (1500, 540), (1500, 584)])
p.edge(msg, whs, "consumes", "rel_async", "exitX=0;exitY=0.7;entryX=0.7;entryY=1;", points=[(1229, 616)])
p.edge(sched, tasks, "RunTask", "rel", "exitX=0;exitY=0.5;entryX=0.5;entryY=0;", points=[(1375, 285)])
p.edge(tasks, s3, "", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
p.node("<b>Deployment decisions</b><br>• ECS rolling with circuit breaker and rollback; Flyway as a prior task (expand/contract)<br>"
       "• Only images with a verified signature are deployed<br>• Each task: least-privilege IAM task role, read-only filesystem and ADOT sidecar<br>• Managed services reached via VPC endpoints<br>"
       "• Terraform (hashicorp/aws); the environment is created for the demo and then destroyed",
       40, 800, 760, 100, "note")
pages.append(p)

# 11. Deployment — GCP
p = Page("11. Deployment · GCP")
p.node("Deployment — GCP (second deployment, same image)", 40, 20, 900, 36, "title")
gh = p.node(lbl("GitHub Actions", "CI/CD", "Workload Identity Federation (keyless)"), 40, 80, 300, 90, "ext")
ar = p.node(lbl("Artifact Registry", "Registry", "signed images"), 400, 80, 200, 95, "db")
p.edge(gh, ar, "push")
user = p.node(lbl("Users and fintechs", "People / systems"), 40, 250, 160, 70, "person")
lb = p.node("<b>Application Load Balancer</b> global<br><span style=\"font-size:10px\">Cloud Armor · managed certificates · URL map</span>", 240, 240, 260, 90, "node")
spa = p.node(lbl("Web console", "Cloud Storage (backend bucket) + Cloud CDN"), 250, 380, 240, 80, "container")
p.edge(user, lb, "HTTPS")
p.edge(lb, spa, "/", "rel", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
runbox = p.node("<b>Cloud Run</b> · serverless NEGs · Direct VPC egress · sidecar OpenTelemetry Collector", 560, 200, 900, 300, "node")
bff = p.node(lbl("console-bff", "Cloud Run service"), 590, 250, 180, 70, "container")
auth = p.node(lbl("auth-server", "Cloud Run service"), 790, 250, 180, 70, "container")
api = p.node(lbl("ledger-app · api", "Cloud Run service"), 990, 250, 180, 70, "container")
whs = p.node(lbl("webhooks", "Cloud Run service"), 1190, 250, 180, 70, "container")
jobs = p.node(lbl("ledger-app · batch / migrate", "Cloud Run Jobs"), 990, 380, 240, 80, "container")
p.edge(lb, bff, "/bff/* · auth.* · api.*", "rel", "exitX=1;exitY=0.3;entryX=0;entryY=0.5;")
p.node("<b>VPC</b> · private subnet", 560, 540, 900, 200, "node")
sql = p.node(lbl("Cloud SQL PostgreSQL", "Private IP · HA · CMEK · PITR", "databases: auth · ledger · sessions · webhooks"), 600, 580, 380, 110, "db")
nat = p.node("<b>Cloud NAT</b> + reserved IP<br><span style=\"font-size:10px\">fixed webhook egress</span>", 1040, 600, 280, 70, "node")
p.edge(runbox, sql, "private IP", "edge_gray", "exitX=0.2;exitY=1;entryX=0.5;entryY=0;")
p.edge(runbox, nat, "webhook egress", "edge_gray", "exitX=0.85;exitY=1;entryX=0.5;entryY=0;")
ps = p.node(lbl("Pub/Sub", "topic + subscription per consumer", "ordering keys · dead-letter topic"), 1540, 250, 220, 100, "queue")
gcs = p.node(lbl("Cloud Storage", "Bucket Lock on anchors", "statements/ · audit-anchors/"), 1540, 520, 220, 100, "db")
p.node(lbl("Secret Manager + Cloud KMS", "rotation"), 1540, 660, 220, 70, "db")
cs = p.node(lbl("Cloud Scheduler", "cron 00:00 COT"), 1540, 400, 220, 70, "ext")
p.node(lbl("Cloud Logging and Monitoring", "alerts · budget"), 1540, 90, 220, 70, "ext")
p.edge(api, ps, "publishes", "rel_async", "exitX=0.5;exitY=0;entryX=0;entryY=0.2;", points=[(1080, 220), (1500, 220), (1500, 270)])
p.edge(ps, whs, "consumes", "rel_async", "exitX=0;exitY=0.7;entryX=1;entryY=0.7;")
p.edge(cs, jobs, "runs", "rel", "exitX=0;exitY=0.5;entryX=1;entryY=0.5;")
p.edge(jobs, gcs, "", "rel", "exitX=1;exitY=0.8;entryX=0;entryY=0.5;", points=[(1500, 444), (1500, 570)])
p.node("<b>Same as AWS</b>: the same signed image, the same schema and the same environment variables. "
       "<b>GCP-specific</b>: the Terraform module (hashicorp/google) and the Pub/Sub event externalizer, enabled by configuration.",
       40, 800, 900, 60, "note")
pages.append(p)

# 12. Deployment — local
p = Page("12. Deployment · local")
p.node("Deployment — local with Floci (the same Terraform as AWS)", 40, 20, 1100, 36, "title")
dev = p.node(lbl("Developer", "Person"), 40, 120, 180, 70, "person")
tf = p.node(lbl("terraform apply", "envs/local", "hashicorp/aws provider with Floci endpoints"), 40, 380, 220, 100, "ext")
p.node("<b>Docker</b> on the developer's machine", 290, 90, 1190, 560, "node")
fl = p.node("<b>Floci</b> · AWS emulator<br><span style=\"font-size:10px\">CloudFront, WAF, ALB, ECS, RDS, SNS/SQS FIFO, S3, Secrets Manager, KMS, IAM, EventBridge Scheduler</span>",
            330, 130, 1120, 70, "node")
spa = p.node(lbl("Web console", "Floci S3, served by its CloudFront"), 340, 240, 220, 80, "container")
p.node("Real containers that Floci starts for ECS and RDS", 330, 350, 1120, 140, "node")
p.node(lbl("console-bff", "container"), 350, 390, 170, 70, "container")
p.node(lbl("auth-server", "container"), 540, 390, 170, 70, "container")
p.node(lbl("ledger-app · api", "container"), 730, 390, 170, 70, "container")
whs = p.node(lbl("webhooks", "container"), 920, 390, 170, 70, "container")
p.node(lbl("PostgreSQL", "RDS container"), 1110, 380, 160, 90, "db")
p.node(lbl("grafana/otel-lgtm", "container", "traces, metrics and logs"), 1110, 520, 200, 100, "container")
mock = p.node(lbl("Test endpoint", "container", "receives webhooks and verifies the signature"), 340, 520, 240, 100, "ext")
p.edge(dev, spa, "HTTPS", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.3;")
p.node("Creates the resources", 160, 300, 110, 30, "edge_label")
p.edge(tf, fl, "", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;", points=[(310, 430), (310, 165)])
p.edge(whs, mock, "Signed webhook", "rel_async", "exitX=0.5;exitY=1;entryX=1;entryY=0.5;", points=[(1005, 570)])
p.node("<b>Tests</b>: Testcontainers starts PostgreSQL and Floci per integration test; Modulith verification and ArchUnit run without containers.",
       40, 690, 1000, 50, "note")
pages.append(p)

# 13. Supplementary — bounded contexts
p = Page("13. Supplementary · bounded contexts")
p.node("Supplementary (DDD) — bounded contexts and context map", 40, 20, 900, 36, "title")
idn = p.node("<b>Identity &amp; Access</b> · generic<br>auth-server<br>users, passkeys, OAuth2 clients", 540, 90, 320, 80, "graybox")
ledc = p.node("<b>Ledger</b> · core<br>Account · Transfer · JournalEntry<br>BusinessDay · FeeSchedule · AccountPolicy", 420, 290, 440, 110, "core")
appr = p.node("<b>Approvals</b> · supporting<br>dual approval", 100, 300, 220, 80, "green")
aud = p.node("<b>Audit</b> · supporting<br>hash chain + anchor", 100, 120, 220, 80, "green")
rec = p.node("<b>Reconciliation</b> · supporting<br>StatementLine · Match · Discrepancy", 420, 520, 300, 90, "green")
wh = p.node("<b>Webhooks</b> · supporting<br>subscriptions and deliveries", 960, 300, 260, 80, "green")
bank = p.node("<b>Bank</b>", 420, 720, 160, 60, "graybox")
fin = p.node("<b>Fintech</b>", 1020, 520, 160, 60, "graybox")
p.edge(ledc, rec, "OHS + PL → client<br>v1 events + journal feed API", "rel", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.edge(bank, rec, "ACL: CSV → StatementLine", "rel", "exitX=0.5;exitY=0;entryX=0.3;entryY=1;")
p.edge(rec, appr, "adjustment proposal", "rel", "exitX=0;exitY=0.3;entryX=0.5;entryY=1;")
p.edge(appr, ledc, "approved<br>adjustment →<br>journal entry", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
p.edge(ledc, aud, "audit log record<br>in the same transaction", "edge_gray", "exitX=0.1;exitY=0;entryX=1;entryY=0.5;")
p.edge(ledc, wh, "PL: v1 events", "rel", "exitX=1;exitY=0.5;entryX=0;entryY=0.5;")
p.edge(wh, fin, "webhook JWS", "rel_async", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.edge(idn, ledc, "Conformist: OAuth2/OIDC<br>(scopes, roles, tenant_id, acr)", "edge_gray", "exitX=0.5;exitY=1;entryX=0.5;entryY=0;")
p.node("<b>Ubiquitous language</b> (console → code)<br>account → Account · journal entry → JournalEntry · posting → Posting<br>"
       "transfer → Transfer · hold → PENDING · fee → Fee<br>business day → BusinessDay · value date → valueDate<br>"
       "bank statement → BankStatement · discrepancy → Discrepancy<br>adjustment → Adjustment · approval → Approval",
       860, 640, 420, 120, "note")
p.node("<b>Explicit exception</b><br>Posting a transfer touches Transfer,<br>2-3 Accounts and creates a JournalEntry<br>in a single transaction: money<br>requires atomicity",
       940, 110, 280, 110, "yellow")
pages.append(p)

# 14. Supplementary — platform contract
p = Page("14. Supplementary · platform contract")
p.node("Supplementary — platform contract: what the app needs and how each cloud provides it", 40, 20, 1300, 36, "title")
p.node("<b>The app only knows</b>: OCI image · environment variables · PostgreSQL 16+ · object storage port · event port · OTLP to a collector and JSON logs to stdout. "
       "No cloud SDK outside an adapter.", 40, 70, 1340, 50, "yellow")
cols = ["Capability", "AWS<br>primary deployment", "GCP<br>second deployment", "Azure<br>reference"]
rows = [
    ["Containers<br>(bff, auth, ledger, webhooks)", "ECS Fargate", "Cloud Run", "Container Apps"],
    ["Scheduled jobs<br>(batch, migrate)", "EventBridge Scheduler<br>+ ECS RunTask", "Cloud Scheduler<br>+ Cloud Run Jobs", "Container Apps Jobs"],
    ["PostgreSQL 16+", "RDS", "Cloud SQL", "Database for PostgreSQL<br>Flexible Server"],
    ["Object storage<br>(bank statements, WORM anchors)", "S3 + Object Lock", "Cloud Storage with HMAC<br>(same S3 adapter) + Bucket Lock", "Blob Storage<br>(own adapter) + immutability"],
    ["Events", "SNS FIFO → SQS FIFO<br>per consumer + DLQ", "Pub/Sub: subscription per consumer<br>ordering keys + dead-letter", "Service Bus: topic<br>+ subscriptions (sessions)"],
    ["Edge: CDN + TLS + WAF", "CloudFront + ACM + WAF v2<br>→ ALB", "Load Balancer + Cloud CDN<br>+ Cloud Armor", "Front Door + WAF"],
    ["Static SPA", "S3 (OAC)", "Cloud Storage (backend bucket)", "Storage static website"],
    ["Fixed-IP egress", "NAT Gateway + Elastic IP", "Cloud NAT + reserved IP", "NAT Gateway"],
    ["Secrets and own keys", "Secrets Manager + KMS", "Secret Manager + Cloud KMS", "Key Vault"],
    ["Observability", "ADOT → CloudWatch", "OTel Collector → Cloud<br>Logging and Monitoring", "Azure Monitor"],
    ["Terraform provider", "hashicorp/aws<br>local: Floci", "hashicorp/google", "hashicorp/azurerm"],
]
x0, y0, cw_, fw, rh = 40, 140, 340, 320, 52
for i, c in enumerate(cols):
    w = fw if i == 0 else cw_
    x = x0 if i == 0 else x0 + fw + (i - 1) * cw_
    p.node(f"<b>{c}</b>", x, y0, w, 44, "core")
for r, row in enumerate(rows):
    for i, c in enumerate(row):
        w = fw if i == 0 else cw_
        x = x0 if i == 0 else x0 + fw + (i - 1) * cw_
        p.node(f"<b>{c}</b>" if i == 0 else c, x, y0 + 44 + r * rh, w, rh, "graybox" if i == 0 else "box")
p.node("<b>Rules</b><br>• Every cloud fulfills the full contract: WAF, own keys and official Terraform<br>"
       "• Standard Postgres only, with Flyway migrations<br>"
       "• Terraform: one module per provider with the same outputs<br>"
       "• The same image runs on AWS and GCP; the event externalizer is chosen by configuration",
       40, 800, 860, 100, "note")
pages.append(p)

out = '<mxfile host="drawio">' + "".join(pg.xml(i) for i, pg in enumerate(pages)) + "</mxfile>"
open(__file__.replace("gen.py", "ledger-architecture.drawio"), "w").write(out)
