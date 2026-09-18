"""
解析 JMeter JTL CSV，给出 Summary Report 关键指标。

Usage: python summarize_jtl.py <jtl_file>
"""
import csv
import statistics
import sys
from collections import defaultdict


def pct(values, p):
    if not values:
        return 0
    s = sorted(values)
    idx = int(len(s) * p / 100)
    return s[min(idx, len(s) - 1)]


def main():
    if len(sys.argv) < 2:
        print("Usage: python summarize_jtl.py <jtl_file>")
        sys.exit(1)
    path = sys.argv[1]
    samples = []
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if not row.get("elapsed") or not row.get("success"):
                continue
            try:
                elapsed = int(row["elapsed"])
                latency = int(row.get("Latency") or 0)
                samples.append({
                    "ts": int(row["timeStamp"]),
                    "elapsed": elapsed,
                    "latency": latency,
                    "success": row["success"].lower() == "true",
                    "code": row["responseCode"],
                    "bytes": int(row.get("bytes") or 0),
                    "url": row.get("URL", ""),
                })
            except (KeyError, ValueError):
                continue
    if not samples:
        print("No valid samples found")
        sys.exit(0)
    total = len(samples)
    success = [s for s in samples if s["success"]]
    failed = [s for s in samples if not s["success"]]
    elapsed_all = [s["elapsed"] for s in samples]
    elapsed_ok = [s["elapsed"] for s in success]
    if not elapsed_all:
        print("No elapsed data")
        sys.exit(0)
    if samples:
        first_ts = samples[0]["ts"]
        last_ts = samples[-1]["ts"]
        duration_s = max(1, (last_ts - first_ts) / 1000)
        rps = total / duration_s
    else:
        duration_s = 0
        rps = 0
    print(f"=== Total: {total} samples, OK {len(success)}, FAIL {len(failed)} ({len(failed) * 100 / total:.2f}%) ===")
    print(f"=== Duration: {duration_s:.1f}s, Throughput: {rps:.2f} req/s ===")
    print()
    print("--- Latency stats (ms) ---")
    print(f"  min:    {min(elapsed_all):>5}")
    print(f"  P50:    {pct(elapsed_all, 50):>5}")
    print(f"  P90:    {pct(elapsed_all, 90):>5}")
    print(f"  P95:    {pct(elapsed_all, 95):>5}")
    print(f"  P99:    {pct(elapsed_all, 99):>5}")
    print(f"  max:    {max(elapsed_all):>5}")
    print(f"  mean:   {statistics.mean(elapsed_all):>5.1f}")
    print()
    if elapsed_ok:
        print("--- Success-only latency (ms) ---")
        print(f"  P50:    {pct(elapsed_ok, 50):>5}")
        print(f"  P95:    {pct(elapsed_ok, 95):>5}")
        print(f"  P99:    {pct(elapsed_ok, 99):>5}")
    print()
    by_code = defaultdict(int)
    for s in samples:
        by_code[s["code"]] += 1
    print("--- Response codes ---")
    for code, count in sorted(by_code.items(), key=lambda x: -x[1]):
        print(f"  {code}: {count} ({count * 100 / total:.1f}%)")
    print()
    if failed:
        print("--- Sample failure URLs (first 5) ---")
        for s in failed[:5]:
            print(f"  {s['code']} {s['url'][:120]}")


if __name__ == "__main__":
    main()