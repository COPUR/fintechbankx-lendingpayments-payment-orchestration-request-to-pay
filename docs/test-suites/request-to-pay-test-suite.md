# Test Suite: Pay Request (Request to Pay)
**Scope:** Request to Pay
**Actors:** Creditor TPP, Debtor PSU, Debtor ASPSP

## 1. Prerequisites
* Creditor TPP is enrolled.
* Debtor PSU is reachable (mobile number/email registered).

## 2. Test Cases

### Suite A: Creation & Notification
| ID | Test Case Description | Input Data | Expected Result | Type |
|----|-----------------------|------------|-----------------|------|
| **TC-RTP-001** | Create Pay Request | Creditor: "Utilities Co", Amount: 500 | `201 Created`, `ConsentId` (PayRequest ID) returned | Functional |
| **TC-RTP-002** | Retrieve Request Status | `ConsentId` | `200 OK`, Status: `AwaitingAuthorisation` | Functional |
| **TC-RTP-003** | Notification Delivery | -- | Debtor receives Push Notification / SMS within SLA | NFR |

### Suite B: Debtor Action
| ID | Test Case Description | Input Data | Expected Result | Type |
|----|-----------------------|------------|-----------------|------|
| **TC-RTP-004** | Debtor Rejects Request | Simulate Rejection in App | `200 OK`, Status updates to `Rejected` | Functional |
| **TC-RTP-005** | Debtor Accepts Request | Simulate Acceptance -> Payment Init | `201 Created` (Payment Initiated), Pay Request Status: `Consumed` | Functional |
| **TC-RTP-006** | Duplicate Acceptance | Try to pay an already `Consumed` request | `400 Bad Request`, Error: `Request already finalized` | Negative |
