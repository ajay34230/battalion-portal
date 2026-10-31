"""Acceptance check C: the generated website has 3+ pages, a viewport tag, a media query, and no broken local links."""
import os
import re
import sys

pages = [f for f in os.listdir('.') if f.endswith('.html')]
assert len(pages) >= 3, pages
bad = []
for p in pages:
    t = open(p, encoding='utf-8').read()
    assert 'name="viewport"' in t, p + ' lacks viewport'
    for ref in re.findall(r'(?:href|src)="([^"#?]+)"', t):
        if ref.startswith(('http', 'mailto:')):
            continue
        if not os.path.exists(ref):
            bad.append((p, ref))
assert '@media' in open('css/site.css', encoding='utf-8').read()
print('pages:', pages, 'broken links:', bad)
sys.exit(1 if bad else 0)
