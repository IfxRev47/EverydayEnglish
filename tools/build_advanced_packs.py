"""Build B1/B2/C1 packs from pinned local sources; --bundle explicitly targets Android assets."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEVELS = {'b1': (805, 905), 'b2': (1429, 1579), 'c1': (1314, 1421)}
LABELS = {'noun': ['n'], 'verb': ['v', 'vt', 'vi'], 'adjective': ['a', 'adj'],
          'adverb': ['adv', 'ad'], 'preposition': ['prep'], 'pronoun': ['pron'],
          'conjunction': ['conj'], 'determiner': ['det', 'a', 'adj'], 'number': ['num'],
          'modal verb': ['aux', 'v', 'vt', 'vi']}

def read(path):
    return json.loads((ROOT / path).read_text(encoding='utf-8'))

def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()

def phonetic(raw):
    # Legacy ECDICT notation -> Unicode stress/length/schwa. Accent is not guaranteed.
    raw = raw.strip().strip('/[]').replace("'", 'ˈ').replace(':', 'ː').replace('ә', 'ə').replace('ɡ', 'g')
    raw = raw.replace('.', 'ˌ').replace('g', 'ɡ')
    assert raw, 'Missing phonetic; add a project override'
    return '/' + raw + '/'

def gloss(raw, pos):
    values = []
    pattern = r'^(?:' + '|'.join(LABELS[pos]) + r')\.\s*(.*)'
    for line in raw.replace('\\n', '\n').splitlines():
        match = re.match(pattern, line.strip())
        if match:
            for value in re.split('[,，;；]', match[1]):
                value = value.strip()
                if value and value not in values:
                    values.append(value)
    assert values, 'Missing POS-aligned gloss; add a project override'
    result = '；'.join(values[:4])
    assert re.search('[\u4e00-\u9fff]', result), 'Gloss has no Chinese'
    return result

def build():
    source = read('data/sources/ecdict_advanced_subset.json')
    overrides = read('data/advanced_overrides.json')
    output = {}
    for level, counts in LEVELS.items():
        path = f'data/oxford_{level}_scope.json'
        scope = read(path)
        assert (len(scope), sum(map(len, scope.values()))) == counts
        words = []
        for key, poses in sorted(scope.items()):
            raw = source['entries'].get(key, {})
            edit = overrides.get(key, {})
            try:
                ipa = edit.get('phonetic') or phonetic(raw.get('phonetic', ''))
                senses = []
                for pos in dict.fromkeys(poses):
                    manual = edit.get('meanings', {}).get(pos)
                    meaning = manual or gloss(raw.get('translation', ''), pos)
                    sense = {'partOfSpeech': pos, 'chineseMeaning': meaning,
                             'contentSource': 'project-authored' if manual else 'ECDICT'}
                    if pos in edit.get('phonetics', {}):
                        sense['phonetic'] = edit['phonetics'][pos]
                        sense['phoneticSource'] = 'project-authored'
                    if pos in edit.get('readings', {}): sense['readings'] = edit['readings'][pos]
                    senses.append(sense)
                words.append({'word': edit.get('word', key), 'phonetic': senses[0].get('phonetic', ipa), 'meanings': senses,
                              'phoneticSource': 'project-authored' if 'phonetic' in edit or 'phonetic' in senses[0] else 'ECDICT (notation normalized)',
                              'reviewStatus': 'needs-editorial-review'})
            except AssertionError as error:
                raise AssertionError(f'{level}/{key}: {error}') from error
        output[level] = {'schemaVersion': 1, 'datasetId': f'oxford-{level}', 'version': 1,
            'cefrLevel': level.upper(), 'releaseStatus': 'prepared-not-bundled',
            'provenance': {
                'scopeUrl': 'https://www.oxfordlearnersdictionaries.com/wordlists/oxford3000-5000',
                'scopeList': 'Oxford 5000', 'scopeAttribute': 'data-ox5000', 'scopeCheckedOn': '2026-10-08',
                'scopeSha256': sha(path), 'dictionaryRepository': source['repository'],
                'officialScopeRowCount': counts[1],
                'homographHandling': 'Repeated word/POS rows are merged into one schema-v1 meaning; tear noun retains both pronunciations and glosses in readings.',
                'dictionaryCommit': source['commit'], 'dictionaryCsvSha256': source['csvSha256'],
                'localDictionarySha256': sha('data/sources/ecdict_advanced_subset.json'),
                'overridesSha256': sha('data/advanced_overrides.json'),
                'dictionaryLicense': source['license'], 'licenseFile': '../licenses/ECDICT-MIT.txt',
                'editorialNotice': 'ECDICT-derived glosses and pronunciation plus project-authored corrections. Not Oxford definitions or pronunciation. Not professionally reviewed; CEFR applies to word/POS, not individual translated senses. Pronunciation accent may vary.',
                'scopeLicenseNotice': 'Public Oxford scope is not an open redistribution license; confirm applicable permission before publication.',
                'audio': 'Android offline TextToSpeech; no Oxford audio bundled'}, 'words': words}
    return output

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--bundle', action='store_true', help='Write/check enabled Android assets instead of staged files')
    args = parser.parse_args()
    packs = build()  # Validate every pack before writing.
    for level, pack in packs.items():
        path = ROOT / (f'app/src/main/assets/words_{level}.json' if args.bundle else f'data/prepared/words_{level}.json')
        if args.bundle:
            pack['releaseStatus'] = 'bundled-personal-learning-needs-editorial-review'
            pack['provenance']['licenseFile'] = 'licenses/ECDICT-MIT.txt'
        encoded = json.dumps(pack, ensure_ascii=False, indent=2) + '\n'
        if args.check:
            assert path.read_text(encoding='utf-8') == encoded, f'Regenerate {path}'
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(encoded, encoding='utf-8')
        print(f'{level.upper()}: {len(pack["words"])} words / {sum(len(w["meanings"]) for w in pack["words"])} POS entries')
