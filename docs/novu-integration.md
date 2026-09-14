# CBS Novu integration

## Setup

1. Apply the tenant Liquibase migrations.
2. In CBS, open **System > External Services > Novu**.
3. Set `api_url` (`https://api.novu.co` for the US cloud), `api_key`, `timeout_seconds`, and `enabled=true`.
4. In Novu, create workflows whose identifiers match the workflow IDs configured under **Organization > Novu campaigns**.
5. Configure workflow steps to use the CBS-managed content fields:
   - Email subject: `{{payload.emailSubject}}`
   - Email body: `{{payload.emailBody}}`
   - SMS body: `{{payload.smsBody}}`
   - Inbox body: `{{payload.inAppBody}}`
   - Chat body (WhatsApp, Telegram, or Slack): `{{payload.chatBody}}`

## Campaign delivery and templates

Novu campaigns use the same delivery concepts as CBS SMS campaigns:

- `DIRECT`: select recipients with a CBS stretchy report and send on demand with
  `POST /api/v1/novu/campaigns/{id}/trigger`.
- `SCHEDULED`: select recipients with a CBS stretchy report and execute from `recurrenceStartDate`. An optional iCalendar
  recurrence rule such as `FREQ=DAILY;INTERVAL=1` repeats the campaign; without a recurrence the campaign runs once.
- `TRIGGERED`: deliver when a configured CBS loan event occurs.

Direct and scheduled campaigns require `reportName`; `paramValue` contains the report parameter JSON. A repayment-reminder
campaign should use a stretchy report that returns installments due in the desired window. Include `id` (or `subscriberId`),
`mobileNo`/`phone`, `email`, and any message variables such as `dueDate`, `amountDue`, and `daysUntilDue` in the report columns.

Message fields accept the familiar campaign syntax `${variableName}` (and also `{{variableName}}`). CBS resolves those values
from the loan event or report row before calling Novu. `GET /api/v1/novu/campaigns/template` returns the SMS campaign business
rules and scheduling choices, supported channels, and a starter variable catalogue for the UI.

WhatsApp, Telegram, and Slack are Novu chat providers. Configure the corresponding integration in the Novu environment, then
store the subscriber's provider credential through `POST /api/v1/novu/subscribers/{subscriberId}/credentials`. The accepted
provider IDs are `whatsapp-business`, `telegram`, and `slack`; the request body follows Novu's provider-credentials structure.
CBS phone numbers can populate normal subscriber data, but Telegram chat IDs and Slack webhook/channel credentials must be
supplied explicitly.

Standard payload values also include `eventType`, `loanId`, `loanAccountNumber`, `clientId`, `clientName`,
`approvedPrincipal`, and `currency`. Repayment events add `transactionId` and `transactionAmount`.

## Events and subscribers

Built-in loan events are `LOAN_CREATED`, `LOAN_APPROVED`, `LOAN_DISBURSED`, `LOAN_REJECTED`, `LOAN_REPAYMENT`,
`LOAN_REPAYMENT_DUE`, `LOAN_OVERDUE`, and `LOAN_CLOSED`. Client subscriber IDs use `client-{id}`. User sync IDs use `user-{id}`; an assigned loan officer
receiving a loan event uses `staff-{id}`. Current email and phone attributes are included whenever a subscriber is
upserted or an event is triggered.

Use the **Sync clients and users** action for an initial bulk import (sent to Novu in batches of 500). Future event
triggers also perform Novu's just-in-time subscriber upsert.

Custom CBS integrations can invoke `POST /api/v1/novu/events/{eventType}/trigger` with:

```json
{
  "subscriber": {
    "subscriberId": "client-123",
    "email": "client@example.org",
    "phone": "+250700000000"
  },
  "payload": {
    "customField": "value"
  }
}
```

Every active campaign mapped to that event is triggered. Delivery acceptance/failure is recorded per configured
channel and is visible only to users with `READ_NOVUNOTIFICATIONLOG`.

## Permissions

- `READ_NOVUCAMPAIGN`
- `CREATE_NOVUCAMPAIGN`
- `UPDATE_NOVUCAMPAIGN`
- `DELETE_NOVUCAMPAIGN`
- `READ_NOVUNOTIFICATIONLOG`
