/* Smart Events - single-page frontend. No build step, no framework:
   a tiny hash router + fetch() against the /api/* routes in WebServer.java. */
(() => {
  "use strict";

  const API = "/api";
  const $app = document.getElementById("app");
  const $top = document.getElementById("topbar");
  const $dlg = document.getElementById("dlg");
  const $toasts = document.getElementById("toasts");

  // ---------------------------------------------------------------
  // Session
  // ---------------------------------------------------------------
  const Session = {
    get token() { return localStorage.getItem("seb_token") || null; },
    get user() { try { return JSON.parse(localStorage.getItem("seb_user")); } catch { return null; } },
    set(token, user) {
      localStorage.setItem("seb_token", token);
      localStorage.setItem("seb_user", JSON.stringify(user));
    },
    setUser(user) { localStorage.setItem("seb_user", JSON.stringify(user)); },
    clear() { localStorage.removeItem("seb_token"); localStorage.removeItem("seb_user"); },
  };

  async function api(method, path, body) {
    const headers = { "Content-Type": "application/json" };
    if (Session.token) headers.Authorization = "Bearer " + Session.token;
    let res;
    try {
      res = await fetch(API + path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
    } catch {
      throw { message: "Can't reach the server. Check your connection and try again.", code: "NETWORK" };
    }
    let data = null;
    try { data = await res.json(); } catch { /* empty body */ }
    if (!res.ok) {
      if (res.status === 401 && Session.token) { Session.clear(); }
      throw (data || { message: "Something went wrong.", code: "UNKNOWN" });
    }
    return data;
  }

  // ---------------------------------------------------------------
  // Small helpers
  // ---------------------------------------------------------------
  function h(tag, attrs, ...kids) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === "html") el.innerHTML = v;
      else if (k.startsWith("on") && typeof v === "function") el.addEventListener(k.slice(2), v);
      else if (k === "class") el.className = v;
      else el.setAttribute(k, v === true ? "" : v);
    }
    for (const kid of kids.flat()) {
      if (kid == null || kid === false) continue;
      el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
    }
    return el;
  }

  const rupee = (n) => "₹" + Number(n).toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 0 });
  const escapeHtml = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  const MONTHS = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"];
  const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  function dateParts(iso) {
    const d = new Date(iso + "T00:00:00");
    return { day: d.getDate(), mo: MONTHS[d.getMonth()], wd: WEEKDAYS[d.getDay()] };
  }
  function formatDateLong(iso) {
    const d = new Date(iso + "T00:00:00");
    return d.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  }

  function toast(message, isError) {
    const t = h("div", { class: "toast" + (isError ? " error" : "") }, message);
    $toasts.append(t);
    setTimeout(() => { t.style.opacity = "0"; t.style.transition = "opacity .25s"; setTimeout(() => t.remove(), 250); }, 3400);
  }

  function errMsg(e) { return (e && e.message) ? e.message : "Something went wrong."; }

  function closeDialog() { if ($dlg.open) $dlg.close(); }
  $dlg.addEventListener("click", (e) => { if (e.target === $dlg) closeDialog(); });
  $dlg.addEventListener("close", () => { $dlg.replaceChildren(); });

  function openDialog(titleText, bodyNode, opts) {
    opts = opts || {};
    const closeBtn = h("button", { class: "icon-btn", "aria-label": "Close", onclick: closeDialog, type: "button" }, "×");
    const wrap = h("div", { class: "dlg-wrap" },
      h("div", { class: "dlg-head" }, h("h2", { id: "dlg-title" }, titleText), closeBtn),
      h("div", { class: "dlg-body" }, bodyNode)
    );
    if (opts.foot) wrap.append(h("div", { class: "dlg-foot" }, opts.foot));
    $dlg.replaceChildren(wrap);
    $dlg.showModal();
    return wrap;
  }

  // ---------------------------------------------------------------
  // Icons (inline SVG, currentColor)
  // ---------------------------------------------------------------
  const icon = {
    logo: () => `<svg width="26" height="26" viewBox="0 0 32 32" fill="none"><rect x="2" y="7" width="28" height="18" rx="3" fill="currentColor"/><circle cx="2" cy="16" r="3.6" fill="var(--paper)"/><circle cx="30" cy="16" r="3.6" fill="var(--paper)"/><path d="M21 9v14" stroke="var(--gold)" stroke-width="2" stroke-dasharray="2.2 2.2"/></svg>`,
    search: () => `<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/></svg>`,
  };

  // ---------------------------------------------------------------
  // Router
  // ---------------------------------------------------------------
  const routes = [];
  function route(pattern, handler) { routes.push({ pattern, handler }); }

  function parseHash() {
    let h2 = location.hash.slice(1) || "/";
    const [pathPart, queryPart] = h2.split("?");
    const segs = pathPart.split("/").filter(Boolean);
    const query = Object.fromEntries(new URLSearchParams(queryPart || ""));
    return { segs, query };
  }

  async function render() {
    const { segs, query } = parseHash();
    renderTopbar(segs);
    for (const r of routes) {
      const m = matchRoute(r.pattern, segs);
      if (m) {
        $app.innerHTML = "";
        try {
          await r.handler({ params: m, query });
        } catch (e) {
          renderError(errMsg(e));
        }
        $app.focus();
        return;
      }
    }
    renderNotFound();
  }

  function matchRoute(pattern, segs) {
    const parts = pattern.split("/").filter(Boolean);
    if (parts.length !== segs.length) return null;
    const params = {};
    for (let i = 0; i < parts.length; i++) {
      if (parts[i].startsWith(":")) params[parts[i].slice(1)] = decodeURIComponent(segs[i]);
      else if (parts[i] !== segs[i]) return null;
    }
    return params;
  }

  function go(path) { location.hash = path; }

  function renderNotFound() {
    $app.replaceChildren(h("div", { class: "wrap" },
      h("div", { class: "empty" },
        h("h2", {}, "Page not found"),
        h("p", {}, "That link doesn't lead anywhere."),
        h("button", { class: "btn", onclick: () => go("/") }, "Back to events")
      )
    ));
  }

  function renderError(msg) {
    $app.replaceChildren(h("div", { class: "wrap" },
      h("div", { class: "empty" }, h("h2", {}, "Couldn't load this page"), h("p", {}, msg))
    ));
  }

  function requireAuth() {
    if (!Session.token) { go("/login"); return false; }
    return true;
  }
  function requireAdmin() {
    if (!requireAuth()) return false;
    if (Session.user?.role !== "ADMIN") { go("/"); return false; }
    return true;
  }

  // ---------------------------------------------------------------
  // Top bar
  // ---------------------------------------------------------------
  function renderTopbar(segs) {
    const user = Session.user;
    const current = segs[0] || "events";
    const navLink = (path, label, key) =>
      h("a", { href: "#" + path, "aria-current": current === key ? "page" : null }, label);

      const nav = [];
    if (user?.role === "ADMIN") {
      nav.push(navLink("/admin", "Events", "admin"), navLink("/admin/analytics", "Analytics", "analytics"), navLink("/admin/checkin", "Sentry Check-In", "admin-checkin"));
    } else {
      nav.push(navLink("/", "Events", "events"));
      if (user) nav.push(navLink("/recommended", "For you", "recommended"), navLink("/bookings", "My bookings", "bookings"), navLink("/passes", "My Passes", "passes"));
    }

    const who = user
      ? h("div", { class: "who" },
          h("a", { href: "#/profile", class: "who-name" },
            h("span", { class: "nm" }, user.name.split(" ")[0]),
            user.role === "ADMIN" ? h("span", { class: "pill admin" }, "Admin") : h("span", { class: "pts" }, user.loyaltyPoints + " pts")),
          h("button", { class: "btn ghost sm", onclick: doLogout }, "Log out"))
      : h("div", { class: "who" },
          h("a", { href: "#/login", class: "btn ghost sm" }, "Log in"),
          h("a", { href: "#/register", class: "btn sm" }, "Sign up"));

    $top.replaceChildren(h("div", { class: "wrap bar" },
      h("a", { href: "#/", class: "brand" }, h("span", { html: icon.logo() }), "Smart Events"),
      h("nav", { class: "nav" }, nav),
      who
    ));
  }

  async function doLogout() {
    try { await api("POST", "/logout"); } catch { /* ignore */ }
    Session.clear();
    toast("Logged out.");
    go("/");
  }

  // ---------------------------------------------------------------
  // Ticket card (shared by browse + recommended)
  // ---------------------------------------------------------------
  function ticketCard(ev) {
    const dp = dateParts(ev.date);
    const soldOut = ev.soldOut;
    const low = !soldOut && ev.occupancy >= 80;
    const surge = ev.surgeMultiplier && ev.surgeMultiplier > 1;

    const tag = ev.reasons && ev.reasons.length
      ? h("span", { class: "tag teal" }, ev.reasons[0])
      : surge ? h("span", { class: "tag gold" }, `${Math.round((ev.surgeMultiplier - 1) * 100)}% surge`)
      : soldOut ? h("span", { class: "tag red" }, "Sold out") : null;

    const card = h("div", { class: "ticket-wrap" },
      h("article", { class: "ticket", tabindex: "0", role: "button", "aria-label": `${ev.name}, ${formatDateLong(ev.date)}`, onclick: () => go(`/events/${ev.id}`), onkeydown: (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); go(`/events/${ev.id}`); } } },
        h("div", { class: "t-main" },
          h("div", { class: "t-date" }, h("div", { class: "mo" }, dp.mo), h("div", { class: "day" }, dp.day), h("div", { class: "wd" }, dp.wd)),
          h("div", { class: "t-body" },
            h("div", { class: "t-cat" }, ev.category),
            h("h3", {}, ev.name),
            h("div", { class: "t-loc" }, ev.location),
            tag)
        ),
        h("div", { class: "t-stub" },
          soldOut
            ? h("div", { class: "t-price out" }, h("strong", {}, "Sold out"))
            : h("div", { class: "t-price" }, h("strong", {}, rupee(ev.currentPrice)), h("span", {}, "per seat")),
          !soldOut && h("div", { class: "t-avail" + (low ? " low" : "") },
            `${ev.availableSeats}/${ev.totalSeats} left`,
            h("span", { class: "meter" + (surge ? " hot" : "") }, h("b", { style: `width:${Math.min(100, ev.occupancy)}%` }))),
          h("a", { class: "btn sm", href: "#/events/" + ev.id, onclick: (e) => e.stopPropagation() }, soldOut ? "Join waitlist" : "Book now")
        )
      )
    );
    return card;
  }

  function ticketGrid(events) {
    if (!events.length) {
      return h("div", { class: "empty" },
        h("h2", {}, "No events here yet"),
        h("p", {}, "Try a different search, or check back soon."));
    }
    return h("div", { class: "tickets" }, events.map(ticketCard));
  }

  // ---------------------------------------------------------------
  // Route: Browse events
  // ---------------------------------------------------------------
  route("/", async ({ query }) => {
    const q = query.q || "";
    const list = h("div", { class: "tickets" }, h("p", { class: "muted" }, "Loading events…"));
    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" },
        h("div", {}, h("h1", {}, "Find your next event"), h("p", {}, "Live pricing moves with demand — book early for the base price.")),
        h("div", { class: "search" },
          h("span", { html: icon.search() }),
          h("input", { type: "search", placeholder: "Search by name, city or category", value: q, "aria-label": "Search events", oninput: debounce((e) => { go("/?q=" + encodeURIComponent(e.target.value)); }, 350) }))
      ),
      list
    ));
    try {
      const events = await api("GET", "/events" + (q ? "?q=" + encodeURIComponent(q) : ""));
      list.replaceWith(ticketGrid(events));
    } catch (e) {
      list.replaceWith(h("div", { class: "empty" }, h("h2", {}, "Couldn't load events"), h("p", {}, errMsg(e))));
    }
  });

  function debounce(fn, ms) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
  }

  // ---------------------------------------------------------------
  // Route: Recommended
  // ---------------------------------------------------------------
  route("/recommended", async () => {
    if (!requireAuth()) return;
    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" }, h("div", {}, h("h1", {}, "Picked for you"), h("p", {}, `Based on your city, favourite category and past bookings.`))),
      h("p", { class: "muted" }, "Loading…")
    ));
    const events = await api("GET", "/events/recommended");
    $app.querySelector(".muted").replaceWith(ticketGrid(events));
  });

  // ---------------------------------------------------------------
  // Route: Event detail + booking dialog
  // ---------------------------------------------------------------
  route("/events/:id", async ({ params }) => {
    const id = params.id;
    $app.append(h("div", { class: "wrap" }, h("p", { class: "muted" }, "Loading event…")));
    let ev;
    try { ev = await api("GET", "/events/" + id); }
    catch (e) { renderError(errMsg(e)); return; }

    const dp = dateParts(ev.date);
    const soldOut = ev.soldOut;

    $app.replaceChildren(h("div", { class: "wrap" },
      h("div", { class: "page-head" }, h("div", {},
        h("div", { class: "t-cat" }, ev.category),
        h("h1", {}, ev.name),
        h("p", {}, `${formatDateLong(ev.date)} · ${ev.location}`)
      )),
      h("div", { class: "two-col" },
        h("div", { class: "panel" },
          h("h2", {}, "Details"),
          h("dl", { class: "pass-meta", style: "grid-template-columns:1fr 1fr;padding:0" },
            h("div", {}, h("dt", {}, "Date"), h("dd", {}, `${dp.wd} ${dp.day} ${dp.mo}`)),
            h("div", {}, h("dt", {}, "Location"), h("dd", {}, ev.location)),
            h("div", {}, h("dt", {}, "Base price"), h("dd", {}, rupee(ev.basePrice) + " / seat")),
            h("div", {}, h("dt", {}, "Seats"), h("dd", {}, `${ev.availableSeats} of ${ev.totalSeats} left`))
          ),
          h("p", { class: "hint", style: "margin-top:1rem" }, "Price rises as seats fill: +20% past 80% full, +40% past 90%, +70% in the last 5%.")
        ),
        h("div", { class: "panel" },
          ev.past
            ? h("div", {}, h("h2", {}, "This event has passed"), h("p", { class: "muted" }, "Booking is closed for past events."))
            : soldOut
            ? h("div", {},
                h("h2", {}, "Sold out"),
                h("p", { class: "muted", style: "margin-bottom:1rem" }, "Join the waitlist — you'll be confirmed automatically if a seat opens up."),
                h("button", { class: "btn wide", onclick: () => openBookingDialog(ev) }, "Join waitlist"))
            : h("div", {},
                h("h2", {}, "Book seats"),
                h("p", { class: "t-price", style: "margin-bottom:1rem" }, h("strong", {}, rupee(ev.currentPrice)), h("span", {}, " per seat right now")),
                h("button", { class: "btn wide", onclick: () => openBookingDialog(ev) }, "Continue to book"))
        )
      )
    ));
  });

  function openBookingDialog(ev) {
    if (!Session.token) { closeDialog(); go("/login"); toast("Log in to book tickets."); return; }
    if (Session.user?.role === "ADMIN") { toast("Admin accounts can't book tickets.", true); return; }

    let seats = 1;
    const max = Math.min(10, ev.soldOut ? 10 : Math.max(1, ev.totalSeats));
    const seatInput = h("input", { type: "number", min: "1", max: "10", value: "1", "aria-label": "Number of seats", inputmode: "numeric" });
    const quoteBox = h("div", { class: "quote" }, h("strong", {}, "…"), h("span", {}, "checking availability"));
    const errBox = h("div", { class: "form-error", hidden: true });
    const methodMusic = h("input", { type: "radio", name: "pm", value: "CARD", checked: true });
    const methodUpi = h("input", { type: "radio", name: "pm", value: "UPI" });
    const credLabel = h("label", {}, "Card number (16 digits)");
    const credInput = h("input", { placeholder: "1234 1234 1234 1234", inputmode: "numeric", maxlength: "19" });
    const submitBtn = h("button", { class: "btn wide", type: "submit" }, "Confirm booking");

    async function refreshQuote() {
      try {
        const q = await api("GET", `/events/${ev.id}/quote?seats=${seats}`);
        if (q.waitlist) {
          quoteBox.className = "quote wait";
          quoteBox.replaceChildren(h("strong", {}, "Join the waitlist"), h("span", {}, `Only ${q.available} seat${q.available === 1 ? "" : "s"} left — you'll be added to the queue.`));
          submitBtn.textContent = "Join waitlist";
        } else {
          const surging = q.perSeat > ev.basePrice;
          quoteBox.className = "quote" + (surging ? " surge" : "");
          quoteBox.replaceChildren(h("strong", {}, rupee(q.total)), h("span", {}, `${rupee(q.perSeat)} × ${seats} seat${seats === 1 ? "" : "s"}` + (surging ? " · demand pricing" : "")));
          submitBtn.textContent = "Confirm booking";
        }
      } catch (e) { quoteBox.replaceChildren(h("span", {}, errMsg(e))); }
    }
    function setSeats(n) {
      seats = Math.max(1, Math.min(max, n));
      seatInput.value = seats;
      refreshQuote();
    }
    seatInput.addEventListener("change", () => setSeats(parseInt(seatInput.value, 10) || 1));

    function updateMethodFields() {
      const isUpi = methodUpi.checked;
      credLabel.textContent = isUpi ? "UPI ID" : "Card number (16 digits)";
      credInput.placeholder = isUpi ? "name@bank" : "1234 1234 1234 1234";
      credInput.value = "";
    }
    methodMusic.addEventListener("change", updateMethodFields);
    methodUpi.addEventListener("change", updateMethodFields);

    const form = h("form", { onsubmit: async (e) => {
      e.preventDefault();
      errBox.hidden = true;
      submitBtn.disabled = true;
      const prevLabel = submitBtn.textContent;
      submitBtn.textContent = "Booking…";
      try {
        const booking = await api("POST", "/bookings", {
          eventId: ev.id, seats, method: methodUpi.checked ? "UPI" : "CARD", credential: credInput.value.trim(),
        });
        try { Session.setUser(await api("GET", "/me")); renderTopbar(parseHash().segs); } catch { /* points refresh is best-effort */ }
        closeDialog();
        showBookingConfirmation(booking);
      } catch (e2) {
        errBox.textContent = errMsg(e2);
        errBox.hidden = false;
        submitBtn.disabled = false;
        submitBtn.textContent = prevLabel;
      }
    } },
      errBox,
      h("div", { class: "field" }, h("label", {}, "Seats"),
        h("div", { class: "stepper" },
          h("button", { type: "button", "aria-label": "Fewer seats", onclick: () => setSeats(seats - 1) }, "–"),
          seatInput,
          h("button", { type: "button", "aria-label": "More seats", onclick: () => setSeats(seats + 1) }, "+")),
        h("p", { class: "hint" }, `Up to ${max} seats per booking.`)),
      quoteBox,
      h("fieldset", { class: "seg" }, h("legend", {}, "Payment method"),
        h("div", { class: "seg-options" },
          h("label", {}, methodMusic, h("span", {}, "Card")),
          h("label", {}, methodUpi, h("span", {}, "UPI")))),
      h("div", { class: "field" }, credLabel, credInput),
      submitBtn
    );

    openDialog(ev.soldOut ? "Join waitlist" : "Book " + ev.name, form);
    refreshQuote();
  }

  function qrSvg(seed) {
    // Deterministic decorative block pattern from the code, matching the
    // console app's ASCII ticket - not a real scannable QR, just a stub look.
    let s = 0; for (const c of seed) s = (s * 31 + c.charCodeAt(0)) >>> 0;
    const n = 9, cell = 176 / n;
    let rects = "";
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
      const corner = (r < 3 && c < 3) || (r < 3 && c >= n - 3) || (r >= n - 3 && c < 3);
      s = (s * 1103515245 + 12345) >>> 0;
      const on = corner ? ((r === 0 || r === 2 || c === 0 || c === 2) && !(r === 1 && c === 1)) ||
                          ((r === 0 || r === 2 || c === n - 1 || c === n - 3) && !(r === 1 && c === n - 2)) ||
                          ((r === n - 1 || r === n - 3 || c === 0 || c === 2) && !(r === n - 2 && c === 1))
                          : (s >>> 24) % 3 === 0;
      if (on) rects += `<rect x="${c * cell}" y="${r * cell}" width="${cell - 1.4}" height="${cell - 1.4}" rx="1.5"/>`;
    }
    return `<svg viewBox="0 0 176 176" role="img" aria-label="Ticket QR code" fill="var(--ink)">${rects}</svg>`;
  }

    function showBookingConfirmation(b) {
    const isWaitlisted = b.status === "WAITLISTED" || b.waitlisted;
    const body = h("div", {},
      h("p", { class: "confirm-copy" }, isWaitlisted
        ? "You''re on the waitlist. We''ll confirm automatically if a seat opens up."
        : "Your seats are booked. Your Sentry Pass has been generated successfully."),
      !isWaitlisted && b.sentryPassId && h("div", { class: "pass" },
        h("div", { class: "pass-top" }, h("h3", {}, "Booking #" + b.id), h("p", {}, "Pass ID: " + b.sentryPassId)),
        h("div", { style: "display: flex; gap: 10px; margin-top: 15px;" }, 
          h("a", { class: "btn", href: "#/passes" }, "View Sentry Pass"),
          h("a", { class: "btn btn-outline", href: "#/bookings" }, "My Bookings")
        )
      ),
      !isWaitlisted && !b.sentryPassId && h("div", { class: "pass" },
        h("div", { class: "pass-top" }, h("h3", {}, b.eventName), h("p", {}, b.eventDate)),
        h("div", { class: "pass-qr" }, h("div", { html: qrSvg(b.qrCode || String(b.id)) }), h("div", { class: "code" }, b.qrCode)),
        h("dl", { class: "pass-meta" },
          h("div", {}, h("dt", {}, "Seats"), h("dd", {}, b.seatLabels)),
          h("div", {}, h("dt", {}, "Paid"), h("dd", {}, rupee(b.pricePaid))),
          h("div", {}, h("dt", {}, "Booking"), h("dd", {}, "#" + b.id)))
      )
    );
    openDialog(isWaitlisted ? "You''re on the waitlist" : "Booking Confirmed!", body, {
      foot: h("button", { class: "btn", onclick: () => { closeDialog(); go("/bookings"); } }, "Close"),
    });
  }

  // ---------------------------------------------------------------
  // Route: My bookings
  // ---------------------------------------------------------------
  route("/bookings", async () => {
    if (!requireAuth()) return;
    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" }, h("h1", {}, "My bookings")),
      h("p", { class: "muted" }, "Loading…")
    ));
    let list;
    try { list = await api("GET", "/bookings"); }
    catch (e) { renderError(errMsg(e)); return; }

    if (!list.length) {
      $app.querySelector(".muted").replaceWith(h("div", { class: "empty" },
        h("h2", {}, "No bookings yet"),
        h("p", {}, "Browse events and grab your seat."),
        h("a", { class: "btn", href: "#/" }, "Browse events")));
      return;
    }
    $app.querySelector(".muted").replaceWith(h("div", { class: "rows" }, list.map(bookingRow)));
  });

  function bookingRow(b) {
    const statusClass = b.status.toLowerCase();
    const pill = h("span", { class: "pill " + statusClass }, b.status === "WAITLISTED" ? `Waitlisted${b.waitlistPosition ? " · #" + b.waitlistPosition : ""}` : b.status[0] + b.status.slice(1).toLowerCase());
    const row = h("div", { class: "row s-" + statusClass },
      h("div", {},
        h("h3", {}, b.eventName), pill,
        h("p", { class: "muted small" }, `${b.eventDate || "—"} · ${b.eventLocation || "—"} · Seats ${b.seatLabels || "—"}`),
        b.status === "CONFIRMED" && h("p", { class: "note small muted" }, `Cancelling now refunds ${rupee(b.refundIfCancelled)} (${b.refundPercent}%).`)
      ),
      h("div", { class: "row-side" },
        h("strong", {}, rupee(b.pricePaid)),
        h("span", { class: "muted small" }, "Booking #" + b.id)
      ),
      h("div", { class: "row-actions" },
        b.status === "CONFIRMED" && b.qrCode && h("button", { class: "btn ghost sm", onclick: () => showTicketDialog(b) }, "View ticket"),
        b.status === "CONFIRMED" && h("button", { class: "btn danger sm", onclick: () => cancelFlow(b, row) }, "Cancel")
      )
    );
    return row;
  }

  function showTicketDialog(b) {
    openDialog(b.eventName, h("div", {},
      h("div", { class: "pass", style: "margin:0" },
        h("div", { class: "pass-top" }, h("h3", {}, b.eventName), h("p", {}, `${b.eventDate} · ${b.eventLocation}`)),
        h("div", { class: "pass-qr" }, h("div", { html: qrSvg(b.qrCode) }), h("div", { class: "code" }, b.qrCode)),
        h("dl", { class: "pass-meta" },
          h("div", {}, h("dt", {}, "Seats"), h("dd", {}, b.seatLabels)),
          h("div", {}, h("dt", {}, "Paid"), h("dd", {}, rupee(b.pricePaid))),
          h("div", {}, h("dt", {}, "Booking"), h("dd", {}, "#" + b.id)))
      )
    ));
  }

  function cancelFlow(b, rowEl) {
    const body = h("div", {},
      h("p", { style: "margin-bottom:1rem" }, `Cancel your booking for ${b.eventName}? You'll be refunded ${rupee(b.refundIfCancelled)} (${b.refundPercent}% — based on how close it is to the event).`)
    );
    const cancelBtn = h("button", { class: "btn solid-danger", onclick: async () => {
      cancelBtn.disabled = true; cancelBtn.textContent = "Cancelling…";
      try {
        const res = await api("POST", `/bookings/${b.id}/cancel`);
        closeDialog();
        toast(`Booking cancelled. Refund: ${rupee(res.refund)}.`);
        rowEl.replaceWith(bookingRow(res.booking));
      } catch (e) { closeDialog(); toast(errMsg(e), true); }
    } }, "Cancel booking");
    openDialog("Cancel booking?", body, { foot: [h("button", { class: "btn ghost", onclick: closeDialog }, "Keep booking"), cancelBtn] });
  }

  // ---------------------------------------------------------------
  // Route: Profile
  // ---------------------------------------------------------------
  route("/profile", async () => {
    if (!requireAuth()) return;
    let user;
    try { user = await api("GET", "/me"); Session.setUser(user); } catch (e) { renderError(errMsg(e)); return; }

    const cityInput = h("input", { id: "pf-city", value: user.city });
    const catInput = h("input", { id: "pf-cat", value: user.preferredCategory });
    const errBox = h("div", { class: "form-error", hidden: true });
    const saveBtn = h("button", { class: "btn", type: "submit" }, "Save changes");

    const form = h("form", { onsubmit: async (e) => {
      e.preventDefault();
      errBox.hidden = true;
      saveBtn.disabled = true; saveBtn.textContent = "Saving…";
      try {
        const updated = await api("PUT", "/me", { city: cityInput.value.trim(), preferredCategory: catInput.value.trim() });
        Session.setUser(updated);
        toast("Profile updated.");
        renderTopbar(["profile"]);
      } catch (e2) { errBox.textContent = errMsg(e2); errBox.hidden = false; }
      finally { saveBtn.disabled = false; saveBtn.textContent = "Save changes"; }
    } },
      errBox,
      h("div", { class: "field" }, h("label", { for: "pf-city" }, "City"), cityInput),
      h("div", { class: "field" }, h("label", { for: "pf-cat" }, "Preferred category"), catInput),
      saveBtn
    );

    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" }, h("h1", {}, "Your profile")),
      h("div", { class: "two-col" },
        h("div", { class: "panel" }, h("h2", {}, "Account"), form),
        h("div", { class: "panel" },
          h("h2", {}, "Loyalty points"),
          h("div", { class: "points" }, user.loyaltyPoints),
          h("p", { class: "muted", style: "margin-top:.5rem" }, "Earn 1 point per ₹100 spent. 100 points = ₹200 off a future booking."))
      )
    ));
  });

  // ---------------------------------------------------------------
  // Route: Login / Register
  // ---------------------------------------------------------------
  route("/login", () => authPage("login"));
  route("/register", () => authPage("register"));

  function authPage(mode) {
    if (Session.token) { go("/"); return; }
    const isLogin = mode === "login";
    const errBox = h("div", { class: "form-error", hidden: true });

    const email = h("input", { type: "email", required: true, autocomplete: "email" });
    const password = h("input", { type: "password", required: true, autocomplete: isLogin ? "current-password" : "new-password" });
    const name = h("input", { required: true, autocomplete: "name" });
    const city = h("input", { required: true, autocomplete: "address-level2" });
    const category = h("input", { required: true, placeholder: "Music, Sports, Tech, Comedy…" });
    const submitBtn = h("button", { class: "btn wide", type: "submit" }, isLogin ? "Log in" : "Create account");

    const form = h("form", { onsubmit: async (e) => {
      e.preventDefault();
      errBox.hidden = true;
      submitBtn.disabled = true; submitBtn.textContent = isLogin ? "Logging in…" : "Creating account…";
      try {
        if (isLogin) {
          const res = await api("POST", "/login", { email: email.value.trim(), password: password.value });
          Session.set(res.token, res.user);
          toast(`Welcome back, ${res.user.name.split(" ")[0]}.`);
          go(res.user.role === "ADMIN" ? "/admin" : "/");
        } else {
          await api("POST", "/register", { name: name.value.trim(), email: email.value.trim(), password: password.value, city: city.value.trim(), preferredCategory: category.value.trim() });
          toast("Account created — log in to continue.");
          go("/login");
        }
      } catch (e2) {
        errBox.textContent = errMsg(e2);
        errBox.hidden = false;
        submitBtn.disabled = false; submitBtn.textContent = isLogin ? "Log in" : "Create account";
      }
    } },
      errBox,
      !isLogin && h("div", { class: "field" }, h("label", { for: "au-name" }, "Full name"), Object.assign(name, { id: "au-name" })),
      h("div", { class: "field" }, h("label", { for: "au-email" }, "Email"), Object.assign(email, { id: "au-email" })),
      h("div", { class: "field" }, h("label", { for: "au-pass" }, "Password"), Object.assign(password, { id: "au-pass", minlength: isLogin ? null : "4" }),
        !isLogin && h("p", { class: "hint" }, "At least 4 characters.")),
      !isLogin && h("div", { class: "form-row" },
        h("div", { class: "field" }, h("label", { for: "au-city" }, "City"), Object.assign(city, { id: "au-city" })),
        h("div", { class: "field" }, h("label", { for: "au-cat" }, "Favourite category"), Object.assign(category, { id: "au-cat" }))),
      submitBtn
    );

    $app.append(h("div", { class: "auth" },
      h("h1", {}, isLogin ? "Welcome back" : "Create your account"),
      h("p", { class: "muted" }, isLogin ? "Log in to book and manage tickets." : "Takes less than a minute."),
      h("div", { class: "seg-options" },
        h("label", {}, h("input", { type: "radio", name: "mode", checked: isLogin, onchange: () => go("/login") }), h("span", {}, "Log in")),
        h("label", {}, h("input", { type: "radio", name: "mode", checked: !isLogin, onchange: () => go("/register") }), h("span", {}, "Sign up"))),
      form,
      isLogin && h("p", { class: "hint", style: "margin-top:1rem" }, "Admin demo: admin@events.com / admin123")
    ));
  }

  // ---------------------------------------------------------------
  // Route: Admin — events
  // ---------------------------------------------------------------
  route("/admin", async () => {
    if (!requireAdmin()) return;
    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" },
        h("h1", {}, "Manage events"),
        h("button", { class: "btn", onclick: () => openEventForm() }, "+ Add event")),
      h("p", { class: "muted" }, "Loading…")
    ));
    let events;
    try { events = await api("GET", "/admin/events"); } catch (e) { renderError(errMsg(e)); return; }
    events.sort((a, b) => a.date.localeCompare(b.date));
    $app.querySelector(".muted").replaceWith(adminEventsTable(events));
  });

  function adminEventsTable(events) {
    if (!events.length) return h("div", { class: "empty" }, h("h2", {}, "No events yet"), h("p", {}, "Add your first event to get started."));
    return h("div", { class: "table-wrap" }, h("table", { class: "tbl" },
      h("thead", {}, h("tr", {}, h("th", {}, "Event"), h("th", {}, "Date"), h("th", {}, "Location"), h("th", { class: "num" }, "Price"), h("th", {}, "Occupancy"), h("th", { class: "acts" }, "Actions"))),
      h("tbody", {}, events.map((e) => {
        const tr = h("tr", {},
          h("td", {}, h("strong", {}, e.name), h("div", { class: "sub" }, e.category)),
          h("td", {}, e.date, e.past && h("div", { class: "sub" }, "Past")),
          h("td", {}, e.location),
          h("td", { class: "num" }, rupee(e.basePrice)),
          h("td", {}, h("div", { class: "occ" }, `${e.totalSeats - e.availableSeats}/${e.totalSeats}`, h("span", { class: "meter" }, h("b", { style: `width:${Math.min(100, e.occupancy)}%` })))),
          h("td", { class: "acts" },
            h("button", { class: "btn ghost sm", onclick: () => openEventForm(e, tr) }, "Edit"),
            " ",
            h("button", { class: "btn danger sm", onclick: () => deleteEventFlow(e, tr) }, "Delete"))
        );
        return tr;
      }))
    ));
  }

  function openEventForm(existing, rowEl) {
    const isEdit = !!existing;
    const errBox = h("div", { class: "form-error", hidden: true });
    const name = h("input", { value: existing?.name || "", required: !isEdit });
    const category = h("input", { value: existing?.category || "", required: !isEdit });
    const location = h("input", { value: existing?.location || "", required: !isEdit });
    const date = h("input", { type: "date", value: existing?.date || "", required: !isEdit });
    const basePrice = h("input", { type: "number", min: "0.01", step: "0.01", value: existing?.basePrice ?? "", required: !isEdit });
    const totalSeats = h("input", { type: "number", min: "1", step: "1", value: existing?.totalSeats ?? "", disabled: isEdit });
    const submitBtn = h("button", { class: "btn wide", type: "submit" }, isEdit ? "Save changes" : "Create event");

    const form = h("form", { onsubmit: async (e) => {
      e.preventDefault();
      errBox.hidden = true;
      submitBtn.disabled = true; submitBtn.textContent = "Saving…";
      try {
        const payload = { name: name.value.trim(), category: category.value.trim(), location: location.value.trim(), date: date.value, basePrice: basePrice.value };
        if (!isEdit) payload.totalSeats = totalSeats.value;
        const saved = isEdit ? await api("PUT", `/admin/events/${existing.id}`, payload) : await api("POST", "/admin/events", payload);
        closeDialog();
        toast(isEdit ? "Event updated." : "Event created.");
        if (isEdit && rowEl) {
          const events = await api("GET", "/admin/events");
          events.sort((a, b) => a.date.localeCompare(b.date));
          document.querySelector(".table-wrap")?.replaceWith(adminEventsTable(events));
        } else {
          go("/admin"); render();
        }
      } catch (e2) {
        errBox.textContent = errMsg(e2); errBox.hidden = false;
        submitBtn.disabled = false; submitBtn.textContent = isEdit ? "Save changes" : "Create event";
      }
    } },
      errBox,
      h("div", { class: "field" }, h("label", {}, "Event name"), name),
      h("div", { class: "form-row" },
        h("div", { class: "field" }, h("label", {}, "Category"), category),
        h("div", { class: "field" }, h("label", {}, "Location"), location)),
      h("div", { class: "form-row" },
        h("div", { class: "field" }, h("label", {}, "Date"), date),
        h("div", { class: "field" }, h("label", {}, "Base price (₹)"), basePrice)),
      h("div", { class: "field" }, h("label", {}, "Total seats"), totalSeats, isEdit && h("p", { class: "hint" }, "Seat count can't be changed once seats may be booked.")),
      submitBtn
    );
    openDialog(isEdit ? "Edit event" : "Add event", form);
  }

  function deleteEventFlow(ev, rowEl) {
    const confirmBtn = h("button", { class: "btn solid-danger", onclick: async () => {
      confirmBtn.disabled = true; confirmBtn.textContent = "Deleting…";
      try {
        await api("DELETE", `/admin/events/${ev.id}`);
        closeDialog();
        toast("Event deleted.");
        rowEl.remove();
      } catch (e) { closeDialog(); toast(errMsg(e), true); }
    } }, "Delete event");
    openDialog("Delete event?", h("p", {}, `This removes "${ev.name}" permanently. Existing bookings for it are kept for records but the event will disappear from listings.`),
      { foot: [h("button", { class: "btn ghost", onclick: closeDialog }, "Cancel"), confirmBtn] });
  }

  // ---------------------------------------------------------------
  // Route: Admin — analytics
  // ---------------------------------------------------------------
  route("/admin/analytics", async () => {
    if (!requireAdmin()) return;
    $app.append(h("div", { class: "wrap" }, h("div", { class: "page-head" }, h("h1", {}, "Analytics")), h("p", { class: "muted" }, "Loading…")));
    let a;
    try { a = await api("GET", "/admin/analytics"); } catch (e) { renderError(errMsg(e)); return; }

    const figures = h("dl", { class: "figures" },
      h("div", { class: "figure" }, h("dt", {}, "Total revenue"), h("dd", {}, rupee(a.totalRevenue))),
      h("div", { class: "figure" }, h("dt", {}, "Revenue today"), h("dd", {}, rupee(a.revenueToday))),
      h("div", { class: "figure" }, h("dt", {}, "Cancellation rate"), h("dd", {}, a.cancellationRate + "%")),
      h("div", { class: "figure" }, h("dt", {}, "Most popular"), h("dd", { class: "text" }, a.mostPopularEvent || "—")),
      h("div", { class: "figure" }, h("dt", {}, "Confirmed"), h("dd", {}, a.confirmedBookings)),
      h("div", { class: "figure" }, h("dt", {}, "Waitlisted"), h("dd", {}, a.waitlistedBookings)),
      h("div", { class: "figure" }, h("dt", {}, "Cancelled"), h("dd", {}, a.cancelledBookings)),
      h("div", { class: "figure" }, h("dt", {}, "Customers"), h("dd", {}, a.customers))
    );

    const table = h("div", { class: "table-wrap" }, h("table", { class: "tbl" },
      h("thead", {}, h("tr", {}, h("th", {}, "Event"), h("th", {}, "Sold"), h("th", {}, "Occupancy"), h("th", { class: "num" }, "Revenue"))),
      h("tbody", {}, a.events.map((e) => h("tr", {},
        h("td", {}, e.name),
        h("td", {}, `${e.sold}/${e.total}`),
        h("td", {}, h("div", { class: "occ" }, e.occupancy + "%", h("span", { class: "meter" }, h("b", { style: `width:${Math.min(100, e.occupancy)}%` })))),
        h("td", { class: "num" }, rupee(e.revenue))
      )))
    ));

    $app.querySelector(".muted").replaceWith(h("div", {}, figures, h("h2", { class: "section-title" }, "By event"), table));
  });

  // ---------------------------------------------------------------
  // Boot
  // ---------------------------------------------------------------
  window.addEventListener("hashchange", render);
  window.addEventListener("DOMContentLoaded", () => { if (!location.hash) location.hash = "#/"; render(); });
  if (document.readyState !== "loading") { if (!location.hash) location.hash = "#/"; render(); }



  // ---------------------------------------------------------------
  // Route: My Passes
  // ---------------------------------------------------------------
  route("/passes", async () => {
    if (!requireAuth()) return;
    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" }, h("h1", {}, "My Sentry Passes")),
      h("div", { id: "passes-list", class: "grid" }, "Loading...")
    ));
    try {
      const passes = await api("GET", "/passes");
      const list = document.getElementById("passes-list");
      list.replaceChildren();
      if (passes.length === 0) {
        list.append(h("p", { class: "empty-state" }, "No passes found."));
      } else {
        for (const p of passes) {
          list.append(h("div", { class: "card card-pass" },
            h("div", { class: "card-body" },
              h("div", { class: "pass-badge " + p.passStatus }, p.passStatus),
              h("h3", {}, p.eventName),
              h("p", {}, p.eventDate + " " + p.eventTime),
              h("p", {}, "Venue: " + p.venue),
              h("p", {}, "Seats: " + p.seatNumbers),
              h("p", {}, "Pass ID: " + p.passId),
              h("button", { class: "btn mt", onclick: () => showPassDetails(p) }, "View Pass")
            )
          ));
        }
      }
    } catch (e) {
      toast(errMsg(e), true);
    }
  });

  function showPassDetails(p) {
    const body = h("div", { class: "sentry-pass-card" },
      h("div", { class: "pass-top" }, h("h2", {}, p.eventName), h("p", {}, p.eventDate + " " + p.eventTime + " | " + p.venue)),
      h("div", { class: "pass-qr" }, h("div", { html: qrSvg(p.qrCode || p.passId) }), h("div", { class: "code" }, p.qrCode)),
      h("dl", { class: "pass-meta" },
        h("div", {}, h("dt", {}, "User"), h("dd", {}, p.userName)),
        h("div", {}, h("dt", {}, "Seats"), h("dd", {}, p.seatNumbers)),
        h("div", {}, h("dt", {}, "Booking ID"), h("dd", {}, "#" + p.bookingId)),
        h("div", {}, h("dt", {}, "Pass ID"), h("dd", {}, p.passId)),
        h("div", {}, h("dt", {}, "Status"), h("dd", {}, p.passStatus))
      )
    );
    openDialog("Digital Sentry Pass", body);
  }

  // ---------------------------------------------------------------
  // Route: Admin Check-In
  // ---------------------------------------------------------------
  route("/admin/checkin", async () => {
    if (!requireAuth()) return;
    if (Session.user.role !== "ADMIN") return go("/");
    
    $app.append(h("div", { class: "wrap" },
      h("div", { class: "page-head" }, h("h1", {}, "Sentry Check-In")),
      h("div", { class: "card", style: "max-width: 500px; margin: 2rem auto; padding: 2rem;" },
        h("h2", {}, "Validate Pass"),
        h("form", { onsubmit: async (e) => {
          e.preventDefault();
          const passId = document.getElementById("checkin-passid").value.trim();
          if (!passId) return;
          const resBox = document.getElementById("checkin-result");
          resBox.hidden = true;
          try {
            const res = await api("POST", "/admin/checkin", { passId });
            resBox.className = "alert success";
            resBox.innerHTML = <h3>? ENTRY APPROVED</h3><p>Pass ID: <b> + res.passId + </b><br>User:  + res.userName + <br>Event:  + res.eventName + <br>Seats:  + res.seatNumbers + </p>;
            resBox.hidden = false;
          } catch (err) {
            resBox.className = "alert error";
            resBox.innerHTML = <h3>? ENTRY DENIED</h3><p> + errMsg(err) + </p>;
            resBox.hidden = false;
          }
        }},
          h("div", { class: "field" },
            h("label", { for: "checkin-passid" }, "Pass ID"),
            h("input", { type: "text", id: "checkin-passid", placeholder: "e.g. SP-2026-XXXX", required: true })
          ),
          h("button", { type: "submit", class: "btn" }, "VALIDATE ENTRY")
        ),
        h("div", { id: "checkin-result", hidden: true, style: "margin-top: 2rem; padding: 1rem; border-radius: 4px;" })
      )
    ));
  });


})();

