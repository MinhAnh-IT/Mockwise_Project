#!/usr/bin/env bash
# Mockwise judge / pipeline watchdog — ALERT ONLY, never mutates state.
#
# Why this exists: a non-UUID testcase id once poison-pilled the judge
# `code-submission` consumer; the retry loop's unrotated docker log grew to
# 167 GB, filled the disk, and crashed Kafka + Postgres. The code fixes
# (ErrorHandlingDeserializer + DLQ, id normalization, log rotation) make the
# pipeline self-heal, but this watchdog is defense-in-depth: it detects the
# early symptoms and shouts. It deliberately does NOT reset offsets / restart
# anything — a timer that blindly mutates Kafka would silently drop real work.
#
# Install (host cron, every 10 min):
#   */10 * * * * /opt/mockwise/scripts/judge-watchdog.sh >/dev/null 2>&1
# Look here when something smells wrong:
#   /var/log/mockwise-watchdog.log         (every run, status line)
#   /var/log/mockwise-watchdog.alert.log   (only when a threshold trips)
#   journalctl -t mockwise-watchdog        (syslog, for alerting hooks)

set -uo pipefail

KAFKA_CT="mockwise-infra-kafka-1"
KAFKA_BIN="/opt/kafka/bin/kafka-consumer-groups.sh"
GROUP="judge-service"
TOPIC="code-submission"
DISK_PCT_MAX=85          # alert when / usage >= this
LAG_MAX=25               # alert when consumer lag >= this
DESER_ERR_MAX=20         # alert when this many deser errors in the window
WINDOW="12m"             # log look-back (a bit > cron interval)

LOG=/var/log/mockwise-watchdog.log
ALOG=/var/log/mockwise-watchdog.alert.log
ts() { date -u +%FT%TZ; }
alerts=0

alert() {  # alert <message>
  alerts=$((alerts + 1))
  echo "$(ts) ALERT $*" | tee -a "$ALOG" >> "$LOG"
  logger -t mockwise-watchdog "ALERT $*" 2>/dev/null || true
}
note() { echo "$(ts) $*" >> "$LOG"; }

# 1) Disk — the failure that started the whole cascade.
disk=$(df -P / | awk 'NR==2{gsub("%","",$5); print $5}')
[ -n "${disk:-}" ] && [ "$disk" -ge "$DISK_PCT_MAX" ] && \
  alert "disk / at ${disk}% (>=${DISK_PCT_MAX}%) — log/volume growth?"

# 2) Containers — any mockwise/judge0 container not running or unhealthy.
bad=$(docker ps -a --format '{{.Names}}\t{{.Status}}' \
      | grep -iE 'mockwise|judge0' \
      | grep -viE 'Up .*(healthy)?$' \
      | grep -ivE '\(healthy\)' || true)
[ -n "$bad" ] && alert "container(s) not healthy: $(echo "$bad" | tr '\n' ';')"

# 3) judge-service consumer group — stuck or lagging on code-submission.
cg=$(docker exec "$KAFKA_CT" "$KAFKA_BIN" --bootstrap-server localhost:9092 \
       --describe --group "$GROUP" 2>/dev/null \
     | awk -v t="$TOPIC" '$2==t {print $3, $5, $6}')   # CURRENT END LAG
if [ -z "$cg" ]; then
  alert "judge consumer group has no assignment for $TOPIC (consumer down?)"
else
  cur=$(echo "$cg" | awk '{print $1}')
  lag=$(echo "$cg" | awk '{print $3}')
  if [ "$cur" = "-" ]; then
    alert "judge consumer never committed an offset on $TOPIC — poison pill?"
  elif [ "${lag:--}" != "-" ] && [ "$lag" -ge "$LAG_MAX" ] 2>/dev/null; then
    alert "judge consumer lag=$lag on $TOPIC (>=${LAG_MAX}) — not draining"
  fi
  note "consumer $TOPIC current=$cur lag=${lag:-?}"
fi

# 4) Deserialization errors in the recent window — the poison-pill signature.
deser=$(docker logs --since "$WINDOW" mockwise-judge-service-1 2>&1 \
        | grep -cE 'SerializationException|InvalidFormatException|Cant deserialize|Can.t deserialize' || true)
[ "${deser:-0}" -ge "$DESER_ERR_MAX" ] && \
  alert "judge logged ${deser} deserialization errors in ${WINDOW} — DLQ working but a bad producer is active"

# 5) DLT depth — informational: how many messages have been shed.
dlt=$(docker exec "$KAFKA_CT" "$KAFKA_BIN" --bootstrap-server localhost:9092 \
        --describe --group "$GROUP" 2>/dev/null | grep -c "${TOPIC}.DLT" || true)
note "status disk=${disk:-?}% containers_bad=$([ -n "$bad" ] && echo yes || echo no) deser_${WINDOW}=${deser:-0} alerts=${alerts}"

exit 0
