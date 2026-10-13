"""Build offline packs from official scope snapshots and original editorial content. No network I/O."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'data/packs.json'
CAPITALIZED = set('january february march april may june july august september october november december monday tuesday wednesday thursday friday saturday sunday'.split())
DISPLAY = {'i': 'I', 'cd': 'CD', 'dvd': 'DVD', 'tv': 'TV', 'ok': 'OK', 't-shirt': 'T-shirt'}


def build(level, metadata):
    scope_path = ROOT / f'data/oxford_{level}_scope.json'
    scope = json.loads(scope_path.read_text(encoding='utf-8'))
    content = {}
    for number, line in enumerate((ROOT / f'data/editorial_{level}.txt').read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip() or line.startswith('#'):
            continue
        word, ipa_field, *glosses = line.split('|')
        assert word not in content, f'Duplicate editorial entry at line {number}: {word}'
        assert word in scope, f'Not in official {level} scope: {word}'
        ipas = ipa_field.split('~')
        assert all(p.strip() for p in ipas), f'Missing IPA: {word}'
        assert len(ipas) == 1 or len(ipas) == len(scope[word]), f'IPA/POS mismatch: {word}'
        assert len(glosses) == len(scope[word]), f'POS/gloss count mismatch: {word}'
        assert all(m.strip() for m in glosses), f'Empty gloss: {word}'
        senses = []
        for index, (pos, gloss) in enumerate(zip(scope[word], glosses)):
            sense = {'partOfSpeech': pos, 'chineseMeaning': gloss}
            if len(ipas) > 1:
                sense['phonetic'] = ipas[index]
            senses.append(sense)
        # Proper capitalization follows the sense: A1 May is a month, A2 may is a modal.
        display = DISPLAY.get(word, word.title() if word in CAPITALIZED and scope[word] == ['noun'] else word)
        content[word] = {'word': display, 'phonetic': ipas[0], 'meanings': senses}
    assert content.keys() == scope.keys(), f'Missing editorial entries: {sorted(scope.keys() - content.keys())}'
    assert len(scope) == metadata['wordCount'] and sum(map(len, scope.values())) == metadata['meaningCount'], 'Scope changed; review counts'
    return {'schemaVersion': 1, 'datasetId': metadata['datasetId'], 'version': metadata['version'], 'cefrLevel': level.upper(),
            'provenance': {
                'scopeUrl': 'https://www.oxfordlearnersdictionaries.com/wordlists/oxford3000-5000',
                'scopeCheckedOn': metadata['scopeCheckedOn'],
                'scopeSha256': hashlib.sha256(scope_path.read_bytes()).hexdigest(),
                'chineseAndIpa': 'Project-authored learning content; not copied from Oxford. Not professionally reviewed.',
                'audio': 'Android offline TextToSpeech; no audio files bundled'
            }, 'words': [content[key] for key in sorted(content)]}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if bundled JSON differs from sources')
    parser.add_argument('--level', default='all', help='a1, a2, or another catalog key; default all')
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding='utf-8'))
    levels = sorted(catalog) if args.level == 'all' else [args.level.lower()]
    # Build and validate every requested pack before writing any file.
    packs = [(level, build(level, catalog[level])) for level in levels]
    for level, pack in packs:
        output = ROOT / f'app/src/main/assets/words_{level}.json'
        encoded = json.dumps(pack, ensure_ascii=False, indent=2) + '\n'
        if args.check:
            assert output.read_text(encoding='utf-8') == encoded, f'Regenerate {output.name}'
        elif not output.exists() or output.read_text(encoding='utf-8') != encoded:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(encoded, encoding='utf-8')
        print(f"{level.upper()}: validated {len(pack['words'])} words / {sum(len(w['meanings']) for w in pack['words'])} meanings.")
