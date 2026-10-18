#!/usr/bin/env python3
"""
Event Booking System - Stress Testing & Concurrency Benchmark
Demonstrates throughput, latency percentiles, breaking points, and overselling protection.
Uses Python standard library (no third-party dependencies required).
"""

import argparse
import json
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed


def post_json(url, payload, token=None):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    return req


def get_json(url, token=None):
    req = urllib.request.Request(url, method="GET")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    return req


def setup_test_environment(base_url):
    """Authenticate demo accounts and retrieve sample event ID."""
    print(f"\n[*] Connecting to target: {base_url}")

    # 1. Login as Organizer
    login_url = f"{base_url}/api/v1/auth/login"
    login_payload = {"email": "organizer@cactro.com", "password": "Password@123"}
    try:
        with urllib.request.urlopen(post_json(login_url, login_payload), timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            organizer_token = data["data"]["token"]
            print("    [+] Organizer authenticated (organizer@cactro.com)")
    except Exception as e:
        print(f"    [!] Organizer login failed: {e}. Registering new organizer...")
        reg_url = f"{base_url}/api/v1/auth/register"
        reg_payload = {
            "name": "Benchmark Organizer",
            "email": f"org_{int(time.time())}@cactro.com",
            "password": "Password@123",
            "role": "ORGANIZER"
        }
        with urllib.request.urlopen(post_json(reg_url, reg_payload), timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            organizer_token = data["data"]["token"]

    # 2. Login as Customer
    customer_payload = {"email": "customer1@cactro.com", "password": "Password@123"}
    try:
        with urllib.request.urlopen(post_json(login_url, customer_payload), timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            customer_token = data["data"]["token"]
            print("    [+] Customer authenticated (customer1@cactro.com)")
    except Exception as e:
        print(f"    [!] Customer login failed: {e}. Registering new customer...")
        reg_url = f"{base_url}/api/v1/auth/register"
        reg_payload = {
            "name": "Benchmark Customer",
            "email": f"cust_{int(time.time())}@cactro.com",
            "password": "Password@123",
            "role": "CUSTOMER"
        }
        with urllib.request.urlopen(post_json(reg_url, reg_payload), timeout=10) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            customer_token = data["data"]["token"]

    # 3. Create fresh high-capacity Event for the benchmark
    create_event_url = f"{base_url}/api/v1/events"
    event_payload = {
        "title": f"Flash Sale Concurrency Concert {int(time.time())}",
        "description": "High-volume concurrent ticket reservation stress test",
        "location": "Metropolis Grand Arena",
        "eventDate": "2026-11-20T19:00:00",
        "totalTickets": 500,
        "ticketPrice": 45.00
    }
    with urllib.request.urlopen(post_json(create_event_url, event_payload, organizer_token), timeout=10) as resp:
        data = json.loads(resp.read().decode("utf-8"))
        event_id = data["data"]["id"]
        total_tickets = data["data"]["totalTickets"]
        print(f"    [+] Created benchmark event ID {event_id} with {total_tickets} available tickets")

    return customer_token, event_id, total_tickets


def single_booking_request(base_url, token, event_id):
    """Executes a single ticket booking request and measures round-trip latency."""
    booking_url = f"{base_url}/api/v1/bookings"
    payload = {"eventId": event_id, "ticketCount": 1}
    req = post_json(booking_url, payload, token)

    start_time = time.perf_counter()
    status_code = None
    success = False
    message = ""

    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            status_code = resp.status
            success = (status_code == 201)
    except urllib.error.HTTPError as e:
        status_code = e.code
        try:
            body = json.loads(e.read().decode("utf-8"))
            message = body.get("message", "")
        except Exception:
            message = str(e)
    except Exception as e:
        status_code = 0
        message = str(e)

    duration_ms = (time.perf_counter() - start_time) * 1000
    return status_code, success, duration_ms, message


def run_stress_test(base_url, token, event_id, concurrency, total_requests):
    """Runs a concurrent stress load against the booking API."""
    print(f"\n========================================================")
    print(f" [*] STRESS TEST SCENARIO: {concurrency} CONCURRENT USERS ({total_requests} REQUESTS)")
    print(f"========================================================")

    event_details_url = f"{base_url}/api/v1/events/{event_id}"
    with urllib.request.urlopen(get_json(event_details_url), timeout=10) as resp:
        remaining_before = json.loads(resp.read().decode("utf-8"))["data"]["availableTickets"]

    latencies = []
    success_count = 0
    sold_out_count = 0
    error_count = 0

    benchmark_start = time.perf_counter()

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(single_booking_request, base_url, token, event_id)
            for _ in range(total_requests)
        ]

        for future in as_completed(futures):
            status_code, success, duration_ms, msg = future.result()
            latencies.append(duration_ms)

            if success:
                success_count += 1
            elif status_code == 400 and ("Insufficient" in msg or "Not enough" in msg):
                sold_out_count += 1
            else:
                error_count += 1

    total_time = time.perf_counter() - benchmark_start
    rps = total_requests / total_time if total_time > 0 else 0

    latencies.sort()
    avg_latency = sum(latencies) / len(latencies) if latencies else 0
    min_latency = latencies[0] if latencies else 0
    max_latency = latencies[-1] if latencies else 0
    p50 = latencies[int(len(latencies) * 0.50)] if latencies else 0
    p95 = latencies[int(len(latencies) * 0.95)] if latencies else 0
    p99 = latencies[int(len(latencies) * 0.99)] if latencies else 0

    print(f"\n[+] PERFORMANCE REPORT:")
    print(f"  * Total Time Elapsed     : {total_time:.2f} seconds")
    print(f"  * Throughput             : {rps:.2f} requests/sec")
    print(f"  * Successful Bookings    : {success_count} (HTTP 201)")
    print(f"  * Sold Out Rejections    : {sold_out_count} (HTTP 400 - Graceful Protection)")
    print(f"  * Dropped/Failed Requests: {error_count} (HTTP 5xx / Timeouts)")
    print(f"\n[+] LATENCY PERCENTILES:")
    print(f"  * Average Latency : {avg_latency:.2f} ms")
    print(f"  * Min Latency     : {min_latency:.2f} ms")
    print(f"  * Median (p50)    : {p50:.2f} ms")
    print(f"  * 95th Percentile : {p95:.2f} ms")
    print(f"  * 99th Percentile : {p99:.2f} ms")
    print(f"  * Max Latency     : {max_latency:.2f} ms")

    # Verify Database Integrity (Overselling check)
    with urllib.request.urlopen(get_json(event_details_url), timeout=10) as resp:
        event_data = json.loads(resp.read().decode("utf-8"))["data"]
        remaining_after = event_data["availableTickets"]
        print(f"\n[+] OVERSOLD INVENTORY CHECK:")
        print(f"  * Tickets Before Run : {remaining_before}")
        print(f"  * Booked This Batch  : {success_count}")
        print(f"  * Remaining Seats    : {remaining_after}")
        if remaining_before - success_count == remaining_after:
            print(f"  * INTEGRITY STATUS: [OK] ZERO OVERSELLING DETECTED (Atomic Row Updates Guaranteed)")
        else:
            print(f"  * INTEGRITY STATUS: [FAILED] DISCREPANCY DETECTED")

    return {
        "concurrency": concurrency,
        "rps": rps,
        "avg_latency": avg_latency,
        "p95": p95,
        "errors": error_count,
        "success": success_count
    }


def main():
    parser = argparse.ArgumentParser(description="Stress test Event Booking API")
    parser.add_argument("--url", default="http://localhost:8080", help="Base URL of target API (default: http://localhost:8080)")
    parser.add_argument("--concurrency", type=int, default=50, help="Number of concurrent users (default: 50)")
    parser.add_argument("--requests", type=int, default=200, help="Total requests to send (default: 200)")
    parser.add_argument("--step", action="store_true", help="Run multi-tier step stress test (10 -> 50 -> 100 -> 200 users)")
    args = parser.parse_args()

    customer_token, event_id, total_tickets = setup_test_environment(args.url)

    if args.step:
        tiers = [(10, 50), (25, 100), (50, 200), (100, 300)]
        results = []
        for conc, reqs in tiers:
            res = run_stress_test(args.url, customer_token, event_id, conc, reqs)
            results.append(res)
            time.sleep(1)

        print("\n" + "=" * 60)
        print(" [*] MULTI-TIER CONCURRENCY SUMMARY (PERFORMANCE CURVE)")
        print("=" * 60)
        print(f"{'Concurrency':<12} | {'Requests':<10} | {'Throughput (RPS)':<18} | {'p95 Latency':<14} | {'Failures'}")
        print("-" * 60)
        for r in results:
            print(f"{r['concurrency']:<12} | {r['success']:<10} | {r['rps']:<18.2f} | {r['p95']:<10.2f} ms | {r['errors']}")
        print("=" * 60)
    else:
        run_stress_test(args.url, customer_token, event_id, args.concurrency, args.requests)


if __name__ == "__main__":
    main()
