#!/usr/bin/env python3
"""Remove daily late fees and keep existing monthly penalties.

Calls POST /loans/{loanId}?command=rectifyLateFees (LoanLateFeeRectifyService):
silently deactivates daily flat late-fee charge rows (no Waive Charges txn per day),
keeps existing monthly penalties (does not invent missing monthlies), then refreshes
loan summary + m_loan_arrears_aging (same arrears update as editing a repayment).

Dry-run is the default. Nothing is changed until you pass --apply.

Examples:
  # One loan
  python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 --tenant default \\
    --username mifos --password password --loan-id 404941 --insecure --apply

  # All loans from discovery SQL (local MySQL)
  python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 --tenant default \\
    --username mifos --password password --insecure \\
    --from-discovery-db --mysql-db fineract_default --apply

  # From a pre-exported id file (one loan_id per line)
  python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 --tenant default \\
    --username mifos --password password --insecure \\
    --loan-ids-file /tmp/rectify_loan_ids.txt --apply
"""

from __future__ import annotations

import argparse
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

DISCOVERY_SQL = """
SELECT DISTINCT l.id AS loan_id
FROM m_loan l
JOIN m_product_loan_charge plc ON plc.product_loan_id = l.product_id
JOIN m_charge c ON c.id = plc.charge_id
    AND c.is_active = 1 AND c.is_penalty = 1
    AND c.charge_time_enum = 9 AND c.charge_calculation_enum = 2
JOIN m_loan_charge lc ON lc.loan_id = l.id AND lc.charge_id = c.id
    AND lc.is_active = 1 AND lc.waived = 0
WHERE l.loan_status_id = 300
GROUP BY l.id, c.id
HAVING SUM(CASE WHEN lc.charge_calculation_enum = 2 THEN 1 ELSE 0 END) > 0
    OR COUNT(DISTINCT DATE(lc.due_for_collection_as_of_date)) > 1
    OR EXISTS (
        SELECT 1
        FROM m_loan_charge lc2
        WHERE lc2.loan_id = l.id AND lc2.charge_id = c.id
          AND lc2.is_active = 1 AND lc2.waived = 0
          AND lc2.due_for_collection_as_of_date IS NOT NULL
        GROUP BY YEAR(lc2.due_for_collection_as_of_date), MONTH(lc2.due_for_collection_as_of_date)
        HAVING COUNT(*) > 1
    )
ORDER BY l.id
"""


class FineractClient:
    def __init__(self, base_url, tenant, username, password, insecure):
        self.base_url = base_url.rstrip("/")
        self.tenant = tenant
        self.username = username
        self.password = password
        self.auth_key = None
        self.ssl_context = ssl._create_unverified_context() if insecure else None

    def authenticate(self):
        body = json.dumps({"username": self.username, "password": self.password}).encode("utf-8")
        response = self._request("POST", "/fineract-provider/api/v1/authentication", body, authenticated=False)
        self.auth_key = response["base64EncodedAuthenticationKey"]

    def rectify_late_fees(self, loan_id):
        return self._request(
            "POST",
            "/fineract-provider/api/v1/loans/%s?command=rectifyLateFees" % loan_id,
            b"{}",
        )

    def _request(self, method, path, body, authenticated=True):
        headers = {"Content-Type": "application/json", "Fineract-Platform-TenantId": self.tenant}
        if authenticated:
            headers["Authorization"] = "Basic " + self.auth_key
        request = urllib.request.Request(self.base_url + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=self.ssl_context, timeout=1800) as response:
                payload = response.read().decode("utf-8")
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError("HTTP %s %s\n%s" % (error.code, path, detail)) from error


def load_loan_ids_from_file(path):
    ids = []
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            ids.append(int(line.split()[0]))
    return ids


def load_loan_ids_from_mysql(host, port, user, password, database):
    env = os.environ.copy()
    if password is not None:
        env["MYSQL_PWD"] = password
    command = [
        "mysql",
        "-h", host,
        "-P", str(port),
        "-u", user,
        "-N",
        "-e", DISCOVERY_SQL,
        database,
    ]
    completed = subprocess.run(command, capture_output=True, text=True, env=env, check=False)
    if completed.returncode != 0:
        raise SystemExit("mysql failed:\n%s" % (completed.stderr or completed.stdout))
    ids = []
    for line in completed.stdout.splitlines():
        line = line.strip()
        if line:
            ids.append(int(line))
    return ids


def summarize(result, loan_id):
    changes = result.get("changes") or {}
    return {
        "loanId": result.get("loanId", loan_id),
        "removedDailyCharges": changes.get("removedDailyCharges"),
        "postedMonthlyCharges": changes.get("postedMonthlyCharges"),
        "arrearsRefreshed": changes.get("arrearsRefreshed"),
        "recomputed": changes.get("recomputed"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True, help="Fineract server, for example https://localhost:8443")
    parser.add_argument("--tenant", default="default")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--loan-id", type=int, action="append", dest="loan_ids", help="Repeatable. Can combine with file/db.")
    parser.add_argument("--loan-ids-file", help="Text file with one loan id per line")
    parser.add_argument("--from-discovery-db", action="store_true", help="Load loan ids via the discovery SQL against MySQL")
    parser.add_argument("--mysql-host", default="localhost")
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--mysql-user", default="root")
    parser.add_argument("--mysql-password", default="mysql")
    parser.add_argument("--mysql-db", default="fineract_default")
    parser.add_argument("--apply", action="store_true", help="Run the correction. Without this flag only lists loan ids.")
    parser.add_argument("--insecure", action="store_true", help="Do not verify the server TLS certificate.")
    parser.add_argument("--continue-on-error", action="store_true", help="Keep going after a loan fails (batch mode).")
    parser.add_argument("--sleep-ms", type=int, default=0, help="Pause between loans (milliseconds).")
    parser.add_argument("--limit", type=int, default=0, help="Process at most N loans (0 = all).")
    parser.add_argument("--offset", type=int, default=0, help="Skip the first N loan ids.")
    parser.add_argument("--progress-file", help="Append JSONL results here (success and failure).")
    parser.add_argument(
        "--skip-ok-from-progress",
        action="store_true",
        help="Skip loan ids that already have status=ok in --progress-file (resume after restart).",
    )
    args = parser.parse_args()

    loan_ids = list(args.loan_ids or [])
    if args.loan_ids_file:
        loan_ids.extend(load_loan_ids_from_file(args.loan_ids_file))
    if args.from_discovery_db:
        loan_ids.extend(
            load_loan_ids_from_mysql(
                args.mysql_host, args.mysql_port, args.mysql_user, args.mysql_password, args.mysql_db
            )
        )
    # de-dupe preserving order
    seen = set()
    unique_ids = []
    for loan_id in loan_ids:
        if loan_id not in seen:
            seen.add(loan_id)
            unique_ids.append(loan_id)
    loan_ids = unique_ids

    if not loan_ids:
        raise SystemExit("No loan ids. Pass --loan-id, --loan-ids-file, and/or --from-discovery-db.")

    if args.skip_ok_from_progress:
        if not args.progress_file:
            raise SystemExit("--skip-ok-from-progress requires --progress-file")
        done = set()
        if os.path.exists(args.progress_file):
            with open(args.progress_file, encoding="utf-8") as handle:
                for line in handle:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        row = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if row.get("status") == "ok" and row.get("loanId") is not None:
                        done.add(int(row["loanId"]))
        before = len(loan_ids)
        loan_ids = [loan_id for loan_id in loan_ids if loan_id not in done]
        print("Skipping %s already-ok loans from progress file." % (before - len(loan_ids)))

    if args.offset:
        loan_ids = loan_ids[args.offset :]
    if args.limit and args.limit > 0:
        loan_ids = loan_ids[: args.limit]

    print("Loans to rectify: %s" % len(loan_ids))
    if not args.apply:
        print("Dry run only. First 20 ids: %s" % (loan_ids[:20],))
        print("Re-run with --apply to change these loans.")
        return

    client = FineractClient(args.base_url, args.tenant, args.username, args.password, args.insecure)
    client.authenticate()

    ok = 0
    failed = 0
    progress = open(args.progress_file, "a", encoding="utf-8") if args.progress_file else None
    try:
        for index, loan_id in enumerate(loan_ids, start=1):
            try:
                result = client.rectify_late_fees(loan_id)
                summary = summarize(result, loan_id)
                ok += 1
                line = json.dumps({"status": "ok", "index": index, "total": len(loan_ids), **summary})
                print(line, flush=True)
                if progress:
                    progress.write(line + "\n")
                    progress.flush()
            except Exception as error:  # noqa: BLE001 - batch must keep going when asked
                failed += 1
                line = json.dumps(
                    {
                        "status": "error",
                        "index": index,
                        "total": len(loan_ids),
                        "loanId": loan_id,
                        "error": str(error),
                    }
                )
                print(line, flush=True)
                if progress:
                    progress.write(line + "\n")
                    progress.flush()
                if not args.continue_on_error:
                    raise SystemExit("Stopped on loan %s. Pass --continue-on-error to keep going." % loan_id) from error
            if args.sleep_ms > 0 and index < len(loan_ids):
                time.sleep(args.sleep_ms / 1000.0)
    finally:
        if progress:
            progress.close()

    print(json.dumps({"done": True, "ok": ok, "failed": failed, "total": len(loan_ids)}), flush=True)
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
