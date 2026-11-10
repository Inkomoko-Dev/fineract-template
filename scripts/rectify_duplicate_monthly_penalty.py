#!/usr/bin/env python3
"""Rectify one loan's late fees via the standalone rectify API.

Calls POST /loans/{loanId}?command=rectifyLateFees. That endpoint is implemented by
LoanLateFeeRectifyService (JDBC + journals only). It does not run the daily late-fee
engine: it waives legacy daily/percentage rows, fixes schedule/summary, and posts any
missing monthly penalties itself.

Insurance and other non-late-fee charges are left alone.

Dry-run is the default. Nothing is changed until you pass --apply.

Example:
  python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 \\
    --tenant default \\
    --username mifos \\
    --password password \\
    --loan-id 404941

  python3 scripts/rectify_duplicate_monthly_penalty.py \\
    --base-url https://localhost:8443 --tenant default \\
    --username mifos --password password --loan-id 404941 --apply
"""

import argparse
import json
import ssl
import sys
import urllib.error
import urllib.request


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
            with urllib.request.urlopen(request, context=self.ssl_context) as response:
                payload = response.read().decode("utf-8")
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            raise SystemExit("HTTP %s %s\n%s" % (error.code, path, detail)) from error


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True, help="Fineract server, for example https://localhost:8443")
    parser.add_argument("--tenant", default="default")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--loan-id", required=True, type=int)
    parser.add_argument("--apply", action="store_true", help="Run the correction. Without this flag the script only prints the call.")
    parser.add_argument("--insecure", action="store_true", help="Do not verify the server TLS certificate.")
    args = parser.parse_args()

    print("Loan %s: standalone rectifyLateFees (waive legacy late fees, fix balances, post missing monthly penalties)." % args.loan_id)
    print("POST /loans/%s?command=rectifyLateFees" % args.loan_id)
    if not args.apply:
        print("\nDry run only. Re-run with --apply to change this loan.")
        return

    client = FineractClient(args.base_url, args.tenant, args.username, args.password, args.insecure)
    client.authenticate()
    result = client.rectify_late_fees(args.loan_id)
    changes = result.get("changes") or {}
    print(json.dumps({
        "loanId": result.get("loanId", args.loan_id),
        "removedCharges": changes.get("removedCharges"),
        "waivedAmount": changes.get("waivedAmount"),
        "postedMonthlyCharges": changes.get("postedMonthlyCharges"),
        "recomputed": changes.get("recomputed"),
    }, indent=2))


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
