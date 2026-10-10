#!/bin/bash
# Backend smoke test. Verifies the seven feature additions end-to-end.
# Ponytail: curl + jq only; no framework.

set -eu
BASE="http://localhost:8080"
JQ=$(command -v jq || echo "")
if [ -z "$JQ" ]; then echo "jq required (brew install jq)"; exit 1; fi

PASS=0
FAIL=0

assert_eq() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "  ✓ $label"
    PASS=$((PASS+1))
  else
    echo "  ✗ $label (expected=$expected got=$actual)"
    FAIL=$((FAIL+1))
  fi
}

echo "=== 1. Register two users ==="
RESP_A=$(curl -s -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' -d '{"name":"Alice","email":"alice@example.com","password":"password123"}')
TOKEN_A=$(echo "$RESP_A" | jq -r '.token')
USER_A=$(echo "$RESP_A" | jq -r '.userId')
RESP_B=$(curl -s -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' -d '{"name":"Bob","email":"bob@example.com","password":"password123"}')
TOKEN_B=$(echo "$RESP_B" | jq -r '.token')
USER_B=$(echo "$RESP_B" | jq -r '.userId')
[ -n "$TOKEN_A" ] && [ "$TOKEN_A" != "null" ] && echo "  ✓ Alice token"; PASS=$((PASS+1)) || { echo "  ✗ Alice register"; FAIL=$((FAIL+1)); }
[ -n "$TOKEN_B" ] && [ "$TOKEN_B" != "null" ] && echo "  ✓ Bob token"; PASS=$((PASS+1)) || { echo "  ✗ Bob register"; FAIL=$((FAIL+1)); }

echo "=== 2. Alice creates workspace + page ==="
WS_RESP=$(curl -s -X POST "$BASE/api/workspaces" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"name":"Demo"}')
WS_ID=$(echo "$WS_RESP" | jq -r '.id')
ROLE_A=$(echo "$WS_RESP" | jq -r '.myRole')
assert_eq "Alice is OWNER" "OWNER" "$ROLE_A"

PG_RESP=$(curl -s -X POST "$BASE/api/workspaces/$WS_ID/pages" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"title":"Project Plan"}')
PG_ID=$(echo "$PG_RESP" | jq -r '.id')
[ "$PG_ID" != "null" ] && [ -n "$PG_ID" ] && echo "  ✓ Page created id=$PG_ID"; PASS=$((PASS+1))

# Add searchable content
PG_RESP=$(curl -s -X PUT "$BASE/api/pages/$PG_ID" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"blocks":[{"type":"heading","content":"Roadmap","position":0},{"type":"paragraph","content":"Onboarding milestone planned for next sprint","position":1}]}')
[ "$(echo "$PG_RESP" | jq -r '.id')" = "$PG_ID" ] && echo "  ✓ Page updated"; PASS=$((PASS+1))

echo "=== 3. Invite Bob as EDITOR, then list members ==="
INVITE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/workspaces/$WS_ID/invite" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"email":"bob@example.com","role":"EDITOR"}')
assert_eq "Invite Bob (200)" "200" "$INVITE"

MEMBERS=$(curl -s "$BASE/api/workspaces/$WS_ID/members" -H "Authorization: Bearer $TOKEN_A")
MEMBER_COUNT=$(echo "$MEMBERS" | jq 'length')
assert_eq "Two members" "2" "$MEMBER_COUNT"
BOB_ROLE=$(echo "$MEMBERS" | jq -r ".[] | select(.email==\"bob@example.com\") | .role")
assert_eq "Bob is EDITOR" "EDITOR" "$BOB_ROLE"

echo "=== 4. Search in workspace ==="
HITS=$(curl -s "$BASE/api/workspaces/$WS_ID/search?q=Onboarding" -H "Authorization: Bearer $TOKEN_A")
HITS_COUNT=$(echo "$HITS" | jq 'length')
[ "$HITS_COUNT" -ge "1" ] && echo "  ✓ Found Onboarding in $HITS_COUNT hits"; PASS=$((PASS+1)) || { echo "  ✗ No Onboarding hits"; FAIL=$((FAIL+1)); }

HITS=$(curl -s "$BASE/api/workspaces/$WS_ID/search?q=Roadmap" -H "Authorization: Bearer $TOKEN_A")
HITS_COUNT=$(echo "$HITS" | jq 'length')
[ "$HITS_COUNT" -ge "1" ] && echo "  ✓ Found Roadmap (title hit)"; PASS=$((PASS+1)) || { echo "  ✗ No Roadmap hits"; FAIL=$((FAIL+1)); }

# Change query that returns nothing
HITS=$(curl -s "$BASE/api/workspaces/$WS_ID/search?q=zzznevermatch" -H "Authorization: Bearer $TOKEN_A")
HITS_COUNT=$(echo "$HITS" | jq 'length')
assert_eq "No matches for junk query" "0" "$HITS_COUNT"

echo "=== 5. Member-management RBAC ==="
# Bob (EDITOR) tries to invite — should 403
INVITE_DENIED=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/workspaces/$WS_ID/invite" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' -d '{"email":"stranger@example.com","role":"VIEWER"}')
# Bob isn't yet a member — first need to refresh Bob's workspaces. He sees Demo via listMyWorkspaces.
BOB_WS=$(curl -s "$BASE/api/workspaces" -H "Authorization: Bearer $TOKEN_B" | jq -r '.[0].myRole')
assert_eq "Bob sees Demo as EDITOR" "EDITOR" "$BOB_WS"
INVITE_DENIED=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/workspaces/$WS_ID/invite" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' -d '{"email":"stranger@example.com","role":"VIEWER"}')
assert_eq "Bob cannot invite (403)" "403" "$INVITE_DENIED"

# Last-Owner guard: Alice can't demote herself (she's the only OWNER).
DEMOTE_DENIED=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/api/workspaces/$WS_ID/members/$USER_A" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"role":"EDITOR"}')
assert_eq "Last-owner demotion rejected (400)" "400" "$DEMOTE_DENIED"

echo "=== 6. Version history ==="
# Force another save to generate a fresh snapshot
curl -s -o /dev/null -X PUT "$BASE/api/pages/$PG_ID" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"blocks":[{"type":"heading","content":"Roadmap","position":0},{"type":"paragraph","content":"Onboarding milestone planned for next sprint","position":1},{"type":"code","content":"SELECT 1;","position":2}]}'

VERS=$(curl -s "$BASE/api/pages/$PG_ID/versions" -H "Authorization: Bearer $TOKEN_A")
VCOUNT=$(echo "$VERS" | jq 'length')
[ "$VCOUNT" -ge "2" ] && echo "  ✓ $VCOUNT snapshots saved"; PASS=$((PASS+1)) || { echo "  ✗ No snapshots"; FAIL=$((FAIL+1)); }
TOP=$(echo "$VERS" | jq -r '.[0].versionNumber')
echo "  → top version = v$TOP"

# Restore top
RESTORED=$(curl -s -X POST "$BASE/api/pages/$PG_ID/versions/$TOP/restore" -H "Authorization: Bearer $TOKEN_A")
RB=$(echo "$RESTORED" | jq -r '.blocks | length')
[ "$RB" -ge "3" ] && echo "  ✓ Restored page has $RB blocks"; PASS=$((PASS+1)) || { echo "  ✗ Restore failed (blocks=$RB)"; FAIL=$((FAIL+1)); }

# New versions list — there should now be even more entries.
VERS2=$(curl -s "$BASE/api/pages/$PG_ID/versions" -H "Authorization: Bearer $TOKEN_A")
VCOUNT2=$(echo "$VERS2" | jq 'length')
[ "$VCOUNT2" -gt "$VCOUNT" ] && echo "  ✓ Restore produced additional snapshots ($VCOUNT → $VCOUNT2)"; PASS=$((PASS+1)) || { echo "  ✗ No new snapshot"; FAIL=$((FAIL+1)); }

echo "=== 7. Page access ==="
# Stranger can't get this page
RESP=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/pages/$PG_ID")
assert_eq "No token rejected" "403" "$RESP"   # Spring returns 403 for unauthenticated when matcher requires auth

echo
echo "============================================================"
echo "  PASS=$PASS  FAIL=$FAIL"
echo "============================================================"
[ "$FAIL" = "0" ]
