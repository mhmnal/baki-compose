#!/usr/bin/env bash
# Read-only check before answering anything about the live ledger.
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== git =="
git status --short
echo
echo "== server data file =="
stat -c '%y  %s bytes  %n' ../grab-tracker/data.json 2>/dev/null || echo "data.json not found"
echo
echo "== live API =="
if curl -fsS --max-time 3 http://127.0.0.1:8787/api/data -o /tmp/baki-preflight.json; then
  python3 - <<'PY'
import json
raw=json.load(open('/tmp/baki-preflight.json'))
d=raw.get('data') or {}
ss=sorted(d.get('shifts',[]), key=lambda s:(s.get('date',''),s.get('start','')))
print('version:', raw.get('version'))
print('shifts:', len(ss), ' entries:', len(d.get('entries',[])))
for s in ss[-3:]:
    print('latest:', s.get('date'), s.get('start'), 'fee', s.get('grabFee'), 'id', s.get('id'))
fees=[e for e in d.get('entries',[]) if e.get('date')==(ss[-1].get('date') if ss else '') and e.get('cat')=='grabfee']
print('latest-date grabfee rows:', [(e.get('id'), e.get('amount'), e.get('note')) for e in fees])
PY
else
  echo "server unavailable — do not answer from cached data"
  exit 1
fi
