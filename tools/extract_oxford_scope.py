"""Extract scope metadata only from a locally saved official word-list HTML page.

No network requests, dictionary-page traversal, definitions or audio downloads.
Usage: python tools/extract_oxford_scope.py path/to/saved-wordlist.html output.json
"""
import argparse
from html import unescape
import json
from pathlib import Path
import re

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("html", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("--level", default="a1", choices=["a1", "a2", "b1", "b2", "c1", "c2"])
parser.add_argument("--list", choices=["ox3000", "ox5000"], default="ox3000", dest="word_list")
parser.add_argument("--keep-homographs", action="store_true", help="Keep repeated word/POS rows for explicit downstream homograph handling")
args = parser.parse_args()
scope = {}
for attrs, body in re.findall(r'<li\s+([^>]*data-hw[^>]*)>(.*?)</li>', args.html.read_text(encoding="utf-8"), re.S):
    attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', attrs))
    if attrs.get("data-" + args.word_list) != args.level:
        continue
    match = re.search(r'<span class="pos">(.*?)</span>', body)
    if not match:
        raise ValueError(f"Missing POS for {attrs['data-hw']}")
    word, pos = unescape(attrs["data-hw"]), unescape(match.group(1))
    if pos in scope.setdefault(word, []) and not args.keep_homographs:
        raise ValueError(f"Duplicate word/POS: {word}/{pos}")
    scope[word].append(pos)
if not scope:
    raise ValueError(f"No {args.level.upper()} scope found; inspect source format instead of guessing levels")
args.output.write_text(json.dumps(scope, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"{len(scope)} words, {sum(map(len, scope.values()))} POS entries")
