package com.vision.assistant.code

/** Ready-made starting projects. They work without any AI model, so coding works offline on every phone. */
object Templates {
    data class Template(val id: String, val title: String, val description: String, val files: Map<String, String>)

    val calculator = Template("calculator", "Calculator (HTML/CSS/JS)", "A working calculator with keyboard support. Logic is separate from the screen so it can be tested.", mapOf(
        "index.html" to """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Calculator</title>
<link rel="stylesheet" href="style.css">
</head>
<body>
<main class="calc">
  <div id="display" class="display" aria-live="polite">0</div>
  <div class="keys">
    <button data-k="C" class="fn">C</button><button data-k="±" class="fn">±</button><button data-k="%" class="fn">%</button><button data-k="/" class="op">÷</button>
    <button data-k="7">7</button><button data-k="8">8</button><button data-k="9">9</button><button data-k="*" class="op">×</button>
    <button data-k="4">4</button><button data-k="5">5</button><button data-k="6">6</button><button data-k="-" class="op">−</button>
    <button data-k="1">1</button><button data-k="2">2</button><button data-k="3">3</button><button data-k="+" class="op">+</button>
    <button data-k="0" class="zero">0</button><button data-k=".">.</button><button data-k="=" class="eq">=</button>
  </div>
</main>
<script src="calc.js"></script>
<script src="app.js"></script>
</body>
</html>
""",
        "style.css" to """* { box-sizing: border-box; }
body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #0b1220; font-family: system-ui, sans-serif; }
.calc { width: min(92vw, 360px); background: #121c30; border-radius: 24px; padding: 16px; box-shadow: 0 10px 40px rgba(0,229,255,.15); }
.display { color: #e6f3ff; font-size: 2.6rem; text-align: right; padding: 18px 12px; overflow-x: auto; white-space: nowrap; }
.keys { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
button { font-size: 1.4rem; padding: 16px 0; border: 0; border-radius: 16px; background: #1c2a45; color: #e6f3ff; }
button:active { transform: scale(.96); }
.op { background: #0a6c7a; } .fn { background: #33415c; } .eq { background: #00bcd4; color: #00222b; }
.zero { grid-column: span 2; }
""",
        "calc.js" to """// Pure calculator logic (no DOM) so it can be tested with Node.
function createCalculator() {
  let current = "0", previous = null, op = null, fresh = true;
  const num = () => parseFloat(current);
  const fmt = (n) => {
    if (!isFinite(n)) return "Error";
    return String(parseFloat(n.toPrecision(12)));
  };
  function apply(a, b, o) {
    switch (o) {
      case "+": return a + b;
      case "-": return a - b;
      case "*": return a * b;
      case "/": return b === 0 ? NaN : a / b;
      default: return b;
    }
  }
  return {
    press(k) {
      if (current === "Error" && k !== "C") return current;
      if (/^[0-9]${'$'}/.test(k)) { current = fresh || current === "0" ? k : current + k; fresh = false; }
      else if (k === ".") { if (fresh) { current = "0."; fresh = false; } else if (!current.includes(".")) current += "."; }
      else if (k === "C") { current = "0"; previous = null; op = null; fresh = true; }
      else if (k === "±") { if (current !== "0") current = current.startsWith("-") ? current.slice(1) : "-" + current; }
      else if (k === "%") { current = fmt(num() / 100); fresh = true; }
      else if (["+", "-", "*", "/"].includes(k)) {
        if (op !== null && !fresh) { current = fmt(apply(previous, num(), op)); }
        previous = num(); op = k; fresh = true;
      } else if (k === "=") {
        if (op !== null) { current = fmt(apply(previous, num(), op)); previous = null; op = null; fresh = true; }
      }
      return current;
    },
    value() { return current; },
  };
}
if (typeof module !== "undefined") module.exports = { createCalculator };
""",
        "app.js" to """const calc = createCalculator();
const display = document.getElementById("display");
function press(k) { display.textContent = calc.press(k); }
document.querySelectorAll("button[data-k]").forEach((b) => b.addEventListener("click", () => press(b.dataset.k)));
document.addEventListener("keydown", (e) => {
  const map = { Enter: "=", Escape: "C", Backspace: "C" };
  const k = map[e.key] || e.key;
  if (/^[0-9.+\-*\/=]${'$'}/.test(k) || k === "C") press(k);
});
""",
        "test.js" to """const assert = require("assert");
const { createCalculator } = require("./calc.js");
function run(keys) { const c = createCalculator(); let v; for (const k of keys.split(" ")) v = c.press(k); return v; }
assert.strictEqual(run("2 + 3 ="), "5");
assert.strictEqual(run("9 / 3 ="), "3");
assert.strictEqual(run("1 / 0 ="), "Error");
assert.strictEqual(run("1 2 * 3 ="), "36");
assert.strictEqual(run("5 0 %"), "0.5");
assert.strictEqual(run("0 . 1 + 0 . 2 ="), "0.3");
assert.strictEqual(run("7 ± + 2 ="), "-5");
assert.strictEqual(run("2 + 3 + 4 ="), "9");
console.log("calculator tests passed");
""",
        "README.md" to "# Calculator\n\nOpen `index.html` (Live Preview). Logic lives in `calc.js`; run `node test.js` to test it.\n",
    ))

    val csvTool = Template("csv-report", "Python CSV utility", "Reads a sample sales CSV, totals it by category and prints a report.", mapOf(
        "report.py" to """import csv
import sys
from collections import defaultdict


def summarise(rows):
    totals = defaultdict(float)
    counts = defaultdict(int)
    for row in rows:
        totals[row["category"]] += float(row["amount"])
        counts[row["category"]] += 1
    return {c: (round(totals[c], 2), counts[c]) for c in sorted(totals)}


def read_rows(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def main(path="sample.csv"):
    summary = summarise(read_rows(path))
    grand = round(sum(t for t, _ in summary.values()), 2)
    print("Category      Total   Count")
    for cat, (total, count) in summary.items():
        print(f"{cat:<12}{total:>8.2f}{count:>8}")
    print(f"{'ALL':<12}{grand:>8.2f}")
    return summary


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "sample.csv")
""",
        "sample.csv" to "date,category,amount\n2026-01-03,food,250.50\n2026-01-04,travel,1200\n2026-01-05,food,99.50\n2026-01-09,books,430\n2026-01-11,travel,300.25\n",
        "test_report.py" to """import unittest
from report import read_rows, summarise


class ReportTest(unittest.TestCase):
    def test_totals(self):
        s = summarise(read_rows("sample.csv"))
        self.assertEqual(s["food"], (350.0, 2))
        self.assertEqual(s["travel"], (1500.25, 2))
        self.assertEqual(s["books"], (430.0, 1))


if __name__ == "__main__":
    unittest.main()
""",
        "README.md" to "# CSV report\n\n`python3 report.py sample.csv` prints totals per category. `python3 test_report.py` runs the tests.\n",
    ))

    private fun page(title: String, active: String, body: String) = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title · My Site</title>
<link rel="stylesheet" href="css/site.css">
</head>
<body>
<header><nav>
  <a href="index.html"${if (active == "home") " class=\"active\"" else ""}>Home</a>
  <a href="about.html"${if (active == "about") " class=\"active\"" else ""}>About</a>
  <a href="contact.html"${if (active == "contact") " class=\"active\"" else ""}>Contact</a>
</nav></header>
<main>
$body
</main>
<footer>© My Site</footer>
</body>
</html>
"""

    val website = Template("website", "Responsive website (3 pages)", "Home, About and Contact pages with shared responsive styling.", mapOf(
        "index.html" to page("Home", "home", "<h1>Welcome</h1>\n<p>This is a responsive website made with VISION. Edit these pages or ask VISION to change them.</p>\n<a class=\"btn\" href=\"contact.html\">Get in touch</a>"),
        "about.html" to page("About", "about", "<h1>About</h1>\n<p>Tell visitors who you are and what you do.</p>"),
        "contact.html" to page("Contact", "contact", "<h1>Contact</h1>\n<form onsubmit=\"event.preventDefault(); this.querySelector('output').textContent = 'Thanks! (demo form: nothing is sent)';\">\n  <label>Name <input required></label>\n  <label>Message <textarea required rows=\"4\"></textarea></label>\n  <button class=\"btn\">Send</button> <output></output>\n</form>"),
        "css/site.css" to """* { box-sizing: border-box; }
body { margin: 0; font-family: system-ui, sans-serif; line-height: 1.6; color: #14202e; background: #f5f9ff; }
header { background: #0b1d33; }
nav { display: flex; gap: 16px; padding: 14px 20px; flex-wrap: wrap; }
nav a { color: #bfe9ff; text-decoration: none; padding: 4px 2px; }
nav a.active { color: #fff; border-bottom: 2px solid #00e5ff; }
main { max-width: 760px; margin: 0 auto; padding: 24px 20px; }
.btn { display: inline-block; padding: 10px 18px; border: 0; border-radius: 10px; background: #00a8c6; color: #fff; text-decoration: none; font-size: 1rem; }
label { display: block; margin: 12px 0; } input, textarea { width: 100%; padding: 10px; border: 1px solid #b6c7da; border-radius: 8px; font: inherit; }
footer { text-align: center; color: #6b7c90; padding: 24px; }
@media (min-width: 700px) { main { padding: 48px 20px; } h1 { font-size: 2.4rem; } }
""",
        "README.md" to "# My Site\n\nOpen `index.html` in Live Preview. Pages link to each other; styles are in `css/site.css`.\n",
    ))

    val all = listOf(calculator, csvTool, website)
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
