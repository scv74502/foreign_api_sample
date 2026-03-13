#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# --- 색상 ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# --- 의존성 확인 ---
for cmd in docker; do
    if ! command -v "$cmd" &>/dev/null; then
        error "'$cmd'이(가) 설치되어 있지 않습니다."
        exit 1
    fi
done

if ! docker compose version &>/dev/null; then
    error "'docker compose'를 사용할 수 없습니다."
    exit 1
fi

# --- OS 감지 및 리소스 계산 ---
detect_resources() {
    local os
    os="$(uname -s)"

    local total_cpus total_mem_mb

    case "$os" in
        Darwin)
            total_cpus=$(sysctl -n hw.ncpu)
            total_mem_mb=$(( $(sysctl -n hw.memsize) / 1024 / 1024 ))
            info "macOS 감지: ${total_cpus} CPUs, ${total_mem_mb}MB RAM"
            ;;
        Linux)
            total_cpus=$(nproc)
            total_mem_mb=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)
            info "Linux 감지: ${total_cpus} CPUs, ${total_mem_mb}MB RAM"
            ;;
        *)
            warn "알 수 없는 OS ($os), 기본값 사용"
            total_cpus=4
            total_mem_mb=8192
            ;;
    esac

    # Docker에 할당할 리소스 풀 (약 60%)
    local pool_cpus pool_mem_mb
    pool_cpus=$(echo "$total_cpus * 0.6" | bc -l 2>/dev/null || echo "$((total_cpus * 6 / 10))")
    pool_mem_mb=$((total_mem_mb * 60 / 100))

    # 최소값 보장
    pool_cpus=$(echo "$pool_cpus" | awk '{if ($1 < 2.0) print 2.0; else print $1}')
    pool_mem_mb=$((pool_mem_mb > 2048 ? pool_mem_mb : 2048))

    # CPU 비율 분배 (app:4, mysql:2, k6:2, wiremock:1 = 총 9)
    local cpu_unit
    cpu_unit=$(echo "$pool_cpus / 9" | bc -l 2>/dev/null || echo "$(echo "scale=2; $pool_cpus / 9" | bc)")

    export APP_CPUS="${APP_CPUS:-$(printf '%.2f' "$(echo "$cpu_unit * 4" | bc -l)")}"
    export MYSQL_CPUS="${MYSQL_CPUS:-$(printf '%.2f' "$(echo "$cpu_unit * 2" | bc -l)")}"
    export K6_CPUS="${K6_CPUS:-$(printf '%.2f' "$(echo "$cpu_unit * 2" | bc -l)")}"
    export WIREMOCK_CPUS="${WIREMOCK_CPUS:-$(printf '%.2f' "$(echo "$cpu_unit * 1" | bc -l)")}"

    # 메모리 비율 분배 (app:4, mysql:4, k6:2, wiremock:1 = 총 11)
    local mem_unit
    mem_unit=$((pool_mem_mb / 11))

    export APP_MEM="${APP_MEM:-$((mem_unit * 4))m}"
    export MYSQL_MEM="${MYSQL_MEM:-$((mem_unit * 4))m}"
    export K6_MEM="${K6_MEM:-$((mem_unit * 2))m}"
    export WIREMOCK_MEM="${WIREMOCK_MEM:-$((mem_unit * 1))m}"

    info "리소스 분배:"
    info "  app:      ${APP_CPUS} CPUs, ${APP_MEM}"
    info "  mysql:    ${MYSQL_CPUS} CPUs, ${MYSQL_MEM}"
    info "  k6:       ${K6_CPUS} CPUs, ${K6_MEM}"
    info "  wiremock: ${WIREMOCK_CPUS} CPUs, ${WIREMOCK_MEM}"
}

# --- 정리 함수 ---
cleanup() {
    info "컨테이너 정리 중..."
    docker compose -f docker-compose.loadtest.yml down -v --remove-orphans 2>/dev/null || true
    ok "정리 완료"
}

# --- 사용법 ---
usage() {
    cat <<EOF
사용법: $0 [옵션]

옵션:
  -h, --help         도움말 표시
  -d, --down         컨테이너 정리 후 종료
  --vus NUM          VU(가상 사용자) 수 (기본값: 20)
  --duration TIME    부하 유지 시간 (기본값: 2m)

환경변수 오버라이드:
  APP_CPUS, MYSQL_CPUS, K6_CPUS, WIREMOCK_CPUS   CPU 할당
  APP_MEM, MYSQL_MEM, K6_MEM, WIREMOCK_MEM        메모리 할당
  K6_VUS, K6_DURATION, K6_RAMP_UP, K6_RAMP_DOWN   k6 설정

예시:
  $0                          # 기본 설정으로 실행
  $0 --vus 50 --duration 5m   # VU 50, 5분 유지
  APP_CPUS=4.0 $0             # 앱 CPU 수동 지정
  $0 --down                   # 정리만 수행
EOF
}

# --- 인자 파싱 ---
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        -d|--down) cleanup; exit 0 ;;
        --vus) export K6_VUS="$2"; shift 2 ;;
        --duration) export K6_DURATION="$2"; shift 2 ;;
        *) error "알 수 없는 옵션: $1"; usage; exit 1 ;;
    esac
done

# --- 결과 디렉토리 (타임스탬프 기반) ---
export RESULT_SUBDIR
RESULT_SUBDIR="$(date '+%y%m%d-%H:%M:%S')"
mkdir -p "$SCRIPT_DIR/testresult/$RESULT_SUBDIR"

# --- 메인 실행 ---
trap cleanup EXIT

detect_resources

info "부하 테스트 환경 시작 중..."
docker compose -f docker-compose.loadtest.yml up --build --abort-on-container-exit 2>&1 | while IFS= read -r line; do
    echo "$line"
done

EXIT_CODE=${PIPESTATUS[0]}

# --- k6 결과 요약 출력 ---
echo ""
echo "============================================"
echo "  k6 부하 테스트 결과 요약"
echo "============================================"

# docker compose 로그에서 k6 요약만 추출
docker compose -f docker-compose.loadtest.yml logs k6 2>/dev/null | grep -E '(✓|✗|█|http_req|iteration|vus|checks|data_|──|│)' || true

# JSON summary 파일 확인
SUMMARY_FILE="$SCRIPT_DIR/testresult/$RESULT_SUBDIR/summary.json"
if [ -f "$SUMMARY_FILE" ]; then
    echo ""
    info "상세 결과 파일: $SUMMARY_FILE"

    # jq가 있으면 핵심 지표 표시
    if command -v jq &>/dev/null; then
        echo ""
        info "핵심 지표:"
        echo -e "  총 요청 수:        $(jq -r '.metrics.http_reqs.values.count // "N/A"' "$SUMMARY_FILE")"
        echo -e "  평균 응답 시간:    $(jq -r '.metrics.http_req_duration.values.avg // "N/A" | if type == "number" then (. | round | tostring) + "ms" else . end' "$SUMMARY_FILE")"
        echo -e "  p95 응답 시간:     $(jq -r '.metrics.http_req_duration.values["p(95)"] // "N/A" | if type == "number" then (. | round | tostring) + "ms" else . end' "$SUMMARY_FILE")"
        echo -e "  요청 실패율:       $(jq -r '.metrics.http_req_failed.values.rate // "N/A" | if type == "number" then (. * 100 | round | tostring) + "%" else . end' "$SUMMARY_FILE")"
        echo -e "  체크 통과율:       $(jq -r '.metrics.checks.values.rate // "N/A" | if type == "number" then (. * 100 | round | tostring) + "%" else . end' "$SUMMARY_FILE")"
    else
        warn "jq가 설치되어 있지 않아 요약을 표시할 수 없습니다. 파일을 직접 확인하세요."
    fi
else
    warn "결과 파일이 생성되지 않았습니다."
fi

echo "============================================"
echo ""

if [ "$EXIT_CODE" -eq 0 ]; then
    ok "부하 테스트 완료"
else
    error "부하 테스트 실패 (exit code: $EXIT_CODE)"
fi

exit "$EXIT_CODE"
