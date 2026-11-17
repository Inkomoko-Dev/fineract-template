#!/usr/bin/env python3
"""Remove daily late fees, rebuild schedule totals, and gap-fill missing monthly penalties.

Calls POST /loans/{loanId}?command=rectifyLateFees (LoanLateFeeRectifyService):
  1. Quietly deactivates daily/flat late-fee charge rows (no Waive Charges txn)
  2. Gap-fills missing Overdue Fees using classic per-installment rules
     (applyChargeToOverdueLoanInstallment): incomplete installments past penalty wait,
     fee frequency/interval, one post before maturity and recurring on/after maturity
     while unpaid, % of installment outstanding principal — not cumulative daily amounts.
     Each gap-fill row is tagged: m_loan_charge.external_id = CGLT-852-GAPFILL:{loanId}:{date}
  3. Syncs schedule/summary so penalty charged/paid/outstanding tally
  4. Refreshes m_loan_arrears_aging

Identify gap-filled charges:
  SELECT * FROM m_loan_charge WHERE external_id LIKE 'CGLT-852-GAPFILL:%' AND is_active = 1;

IRREVERSIBLE on production: there is no undo. Always:
  - Deploy the build that includes classic per-installment gap-fill (CGLT-852)
  - Pause the daily late-fee / overdue penalty job for the batch window
  - Dry-run first (default), then pilot ONE loan, verify Charges + summary, then batch
  - After a code fix, do NOT resume an old progress file (it skips already-ok loans).
    Use a new --progress-file or pass --no-skip-ok-from-progress

Dry-run is the default. Nothing is changed until you pass --apply.
--apply also requires CONFIRM_RECTIFY=YES (production safety gate).

Examples:
  # Pilot one loan (after deploy)
  CONFIRM_RECTIFY=YES python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 --tenant default \\
    --username mifos --password password --loan-id 387264 --insecure \\
    --no-progress-file --apply

  # Dry-run discovery only
  python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 --tenant default \\
    --username mifos --password password --insecure \\
    --from-discovery-db --mysql-db fineract_default

  # Batch from id file (new progress file after a code change)
  CONFIRM_RECTIFY=YES python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://cbs.example.com --tenant default \\
    --username mifos --password '***' \\
    --cert /path/to/client.crt --key /path/to/client.key \\
    --loan-ids-file /tmp/rectify_loan_ids.txt \\
    --progress-file /tmp/rectify_loan_ids.gapfill-v2.progress.jsonl \\
    --apply --continue-on-error --sleep-ms 200
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

# Active overdue-% loans that still need cleanup and/or months after the last posted penalty.
DISCOVERY_SQL = """
SELECT DISTINCT l.id AS loan_id
FROM m_loan l
JOIN m_product_loan_charge plc ON plc.product_loan_id = l.product_id
JOIN m_charge c ON c.id = plc.charge_id
    AND c.is_active = 1 AND c.is_penalty = 1
    AND c.charge_time_enum = 9 AND c.charge_calculation_enum = 2
WHERE l.loan_status_id = 300
AND (
    EXISTS (
        SELECT 1 FROM m_loan_charge lc
        WHERE lc.loan_id = l.id AND lc.charge_id = c.id
          AND lc.is_active = 1 AND lc.waived = 0
          AND lc.charge_calculation_enum <> 2
    )
    OR EXISTS (
        SELECT 1
        FROM m_loan_charge lc2
        WHERE lc2.loan_id = l.id AND lc2.charge_id = c.id
          AND lc2.is_active = 1 AND lc2.waived = 0
          AND lc2.due_for_collection_as_of_date IS NOT NULL
        GROUP BY YEAR(lc2.due_for_collection_as_of_date), MONTH(lc2.due_for_collection_as_of_date)
        HAVING COUNT(*) > 1
    )
    OR (
        EXISTS (
            SELECT 1 FROM m_loan_repayment_schedule rs
            WHERE rs.loan_id = l.id
              AND rs.recalculated_interest_component = 0
              AND coalesce(rs.principal_amount, 0) > 0
              AND greatest(
                    coalesce(rs.principal_amount, 0) - coalesce(rs.principal_completed_derived, 0)
                    - coalesce(rs.principal_writtenoff_derived, 0), 0
                  ) > 0
              AND rs.duedate < CURDATE()
        )
        AND (
            SELECT MAX(lc3.due_for_collection_as_of_date)
            FROM m_loan_charge lc3
            WHERE lc3.loan_id = l.id AND lc3.charge_id = c.id
              AND lc3.is_active = 1 AND lc3.waived = 0
              AND lc3.charge_calculation_enum = 2
        ) < DATE_FORMAT(CURDATE(), '%Y-%m-01')
    )
)
ORDER BY l.id
"""


def build_ssl_context(insecure=False, cert=None, key=None, cacert=None, key_password=None):
    """Build an SSLContext; load a client cert when the gateway requires mTLS."""
    if insecure:
        context = ssl._create_unverified_context()
    elif cacert:
        context = ssl.create_default_context(cafile=cacert)
    else:
        context = ssl.create_default_context()
    if cert:
        context.load_cert_chain(certfile=cert, keyfile=key or cert, password=key_password)
    return context


class FineractClient:
    def __init__(self, base_url, tenant, username, password, ssl_context):
        self.base_url = base_url.rstrip("/")
        self.tenant = tenant
        self.username = username
        self.password = password
        self.auth_key = None
        self.ssl_context = ssl_context

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
        "chargeDefinitionId": changes.get("chargeDefinitionId"),
        "chargeName": changes.get("chargeName"),
        "penaltyPercentFromCharge": changes.get("penaltyPercentFromCharge"),
        "feeIntervalMonths": changes.get("feeIntervalMonths"),
        "penaltyGraceDays": changes.get("penaltyGraceDays"),
        "orphanPenaltyPaidMovedToPrincipal": changes.get("orphanPenaltyPaidMovedToPrincipal"),
        "penaltyCharged": changes.get("penaltyCharged"),
        "penaltyPaid": changes.get("penaltyPaid"),
        "penaltyOutstanding": changes.get("penaltyOutstanding"),
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
    parser.add_argument("--cert", help="Client certificate PEM for mTLS (required by some prod gateways).")
    parser.add_argument("--key", help="Client private key PEM for mTLS (defaults to --cert if omitted).")
    parser.add_argument("--cacert", help="CA bundle PEM used to verify the server certificate.")
    parser.add_argument("--key-password", help="Password for an encrypted client private key.")
    parser.add_argument("--continue-on-error", action="store_true", help="Keep going after a loan fails (batch mode).")
    parser.add_argument("--sleep-ms", type=int, default=0, help="Pause between loans (milliseconds).")
    parser.add_argument("--limit", type=int, default=0, help="Process at most N loans (0 = all).")
    parser.add_argument("--offset", type=int, default=0, help="Skip the first N loan ids.")
    parser.add_argument(
        "--progress-file",
        help="Append JSONL results here. Default with --loan-ids-file: <ids-file>.progress.jsonl",
    )
    parser.add_argument(
        "--skip-ok-from-progress",
        dest="skip_ok_from_progress",
        action="store_true",
        default=None,
        help="Skip loan ids already status=ok in the progress file (default: on when a progress file is used).",
    )
    parser.add_argument(
        "--no-skip-ok-from-progress",
        dest="skip_ok_from_progress",
        action="store_false",
        help="Process every id even if already ok in the progress file (use after a code fix).",
    )
    parser.add_argument(
        "--no-progress-file",
        action="store_true",
        help="Do not write/resume from a progress file (even with --loan-ids-file).",
    )
    args = parser.parse_args()
    if args.key and not args.cert:
        raise SystemExit("--key requires --cert")

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

    progress_file = args.progress_file
    if args.no_progress_file:
        progress_file = None
    elif not progress_file and args.loan_ids_file:
        progress_file = args.loan_ids_file + ".progress.jsonl"

    skip_ok = args.skip_ok_from_progress
    if skip_ok is None:
        skip_ok = bool(progress_file)

    total_in_file = len(loan_ids)
    if skip_ok:
        if not progress_file:
            raise SystemExit("--skip-ok-from-progress requires a progress file")
        done = set()
        if os.path.exists(progress_file):
            with open(progress_file, encoding="utf-8") as handle:
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
        print(
            "Resume: skipping %s already-ok loans (%s remaining of %s). Progress: %s"
            % (before - len(loan_ids), len(loan_ids), total_in_file, progress_file)
        )
        if before > len(loan_ids):
            print(
                "NOTE: after deploying a gap-fill fix, use a NEW --progress-file or --no-skip-ok-from-progress "
                "or those skipped loans will keep the old incomplete monthlies.",
                flush=True,
            )

    if args.offset:
        loan_ids = loan_ids[args.offset :]
    if args.limit and args.limit > 0:
        loan_ids = loan_ids[: args.limit]

    print("Loans to rectify: %s" % len(loan_ids))
    if progress_file:
        print("Progress file: %s" % progress_file)
    if not args.apply:
        print("Dry run only. First 20 ids: %s" % (loan_ids[:20],))
        print("Re-run with --apply and CONFIRM_RECTIFY=YES to change these loans.")
        return

    if os.environ.get("CONFIRM_RECTIFY") != "YES":
        raise SystemExit(
            "Refusing --apply: this mutation is irreversible on production.\n"
            "Pilot one loan first, pause the late-fee job, then re-run with CONFIRM_RECTIFY=YES --apply."
        )

    print(
        "CONFIRM_RECTIFY=YES: applying irreversible rectifyLateFees to %s loan(s). Late-fee job should be paused."
        % len(loan_ids),
        flush=True,
    )

    if not loan_ids:
        print(json.dumps({"done": True, "ok": 0, "failed": 0, "total": 0, "alreadyComplete": True}), flush=True)
        return

    ssl_context = build_ssl_context(
        insecure=args.insecure,
        cert=args.cert,
        key=args.key,
        cacert=args.cacert,
        key_password=args.key_password,
    )
    if not args.cert:
        print(
            "Warning: no --cert/--key provided. Prod gateways that require mTLS will return HTTP 403.",
            flush=True,
        )

    client = FineractClient(args.base_url, args.tenant, args.username, args.password, ssl_context)
    client.authenticate()

    ok = 0
    failed = 0
    progress = open(progress_file, "a", encoding="utf-8") if progress_file else None
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
                    os.fsync(progress.fileno())
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
                    os.fsync(progress.fileno())
                if not args.continue_on_error:
                    print(
                        "Stopped on loan %s. Re-run the same command to resume; already-ok loans are skipped "
                        "unless you use a new progress file or --no-skip-ok-from-progress."
                        % loan_id,
                        flush=True,
                    )
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
