## Purpose

The driver learns about each decision by e-mail, and only about decisions that were actually recorded.

## ADDED Requirements

### Requirement: Decisions are mailed after commit

The e-mails of the driver's approval and rejection MUST be sent only after the decision's transaction commits. A decision that rolls back MUST NOT send an e-mail.

#### Scenario: Approval that fails to commit

- **WHEN** the approval's transaction rolls back
- **THEN** no approval e-mail is sent

### Requirement: The driver hears about a rejected submission

When a driver document, a vehicle document or a CNH is rejected, the driver MUST receive, after commit, an e-mail with the document's name, the van's plate when it is a van's document, the reason of the code and the message when there is one, in the driver's language. After the e-mail is sent the submission MUST record `notifiedAt`. A failed send MUST be logged and leave `notifiedAt` empty.

#### Scenario: The inspection was unreadable

- **WHEN** an admin rejects a van's inspection as `ILLEGIBLE`
- **THEN** the driver gets an e-mail naming the inspection, the plate and the reason, and the inspection has `notifiedAt`
