# notification-delivery Specification

## Purpose
TBD - created by archiving change async-job-queue. Update Purpose after archive.
## Requirements
### Requirement: Notification events store the notification and enqueue the push in the business transaction
The system SHALL, for each notification-producing domain event, store one in-app notification per recipient and enqueue one `SEND_PUSH` job per stored notification, in the transaction that published the event. It SHALL NOT call FCM in the request thread. The events are: chat message sent, proposal sent, proposal accepted, proposal declined, contract created (client and driver), and driver reviewed (approved or rejected).

#### Scenario: Event, notification and job commit together
- **WHEN** a proposal is sent and its transaction commits
- **THEN** a `notification` row exists for the client of that proposal and a `SEND_PUSH` job references its token

#### Scenario: Rolled-back event leaves nothing
- **WHEN** the transaction that published the event rolls back
- **THEN** neither a notification nor a job exists

#### Scenario: Contract created notifies both parties
- **WHEN** a contract is created
- **THEN** two notifications and two jobs exist, one for the client's user and one for the driver's user

#### Scenario: Notification visible before the push
- **WHEN** the request returns
- **THEN** the recipient already sees the notification in the in-app list, whether or not the push was sent

#### Scenario: Chat title uses the sender name
- **WHEN** a chat message event is handled
- **THEN** the stored title is the pt-BR chat title with the sender's name

### Requirement: Notification handling never breaks the business transaction
The system SHALL NOT make the publishing transaction fail because a recipient lookup found nothing.

#### Scenario: Conversation not found
- **WHEN** a chat message event refers to a conversation that no longer resolves
- **THEN** no notification or job is created, a warning is logged and the message transaction still commits

#### Scenario: Recipient not found
- **WHEN** a proposal event refers to a user token that does not resolve
- **THEN** no notification or job is created, a warning is logged and the proposal transaction still commits

### Requirement: The push job sends the stored notification
The system SHALL, when a `SEND_PUSH` job runs, load the notification by token and send it to the recipient's devices with the notification type and token in the push data.

#### Scenario: Push sent
- **WHEN** the job runs for a user with registered devices
- **THEN** a push carrying the notification's title, body, type and token is sent to each device and the job completes

#### Scenario: Push disabled or no devices
- **WHEN** the job runs and push is not configured or the user has no device tokens
- **THEN** the job completes without error

#### Scenario: Notification no longer exists
- **WHEN** the job runs and the notification token does not resolve
- **THEN** the job moves to `failed_jobs` as permanent

### Requirement: Transient FCM failures are retried, stale tokens are not
The system SHALL fail the push job for retry when FCM fails transiently, and SHALL remove stale device tokens without a retry.

#### Scenario: FCM unavailable
- **WHEN** FCM fails the whole batch or every device fails with a transient error
- **THEN** the job is rescheduled under the `SEND_PUSH` retry policy and the in-app notification is not stored again

#### Scenario: Some devices fail transiently
- **WHEN** some devices receive the push and others fail with a transient error
- **THEN** the job is rescheduled for the failed devices only

#### Scenario: Stale token
- **WHEN** a device fails with `UNREGISTERED` or `INVALID_ARGUMENT`
- **THEN** that device token is removed and is not retried

#### Scenario: Retries exhausted
- **WHEN** the push still fails after the last attempt
- **THEN** the job moves to `failed_jobs` and is eligible for the scheduled replay

### Requirement: Delivery survives restarts and load
The system SHALL keep an enqueued push until a worker delivers it or moves it to `failed_jobs`, without a memory limit that drops it.

#### Scenario: Restart before delivery
- **WHEN** the application restarts after a push was enqueued and before it ran
- **THEN** the worker sends it after the restart

#### Scenario: Burst of events
- **WHEN** more events are produced in a short time than the worker handles per cycle
- **THEN** every push is eventually sent or moved to `failed_jobs`, and none is dropped

### Requirement: The in-memory push executor is removed
The system SHALL NOT keep the `push-` executor or its drop-when-full behavior.

#### Scenario: No async executor for push
- **WHEN** the application context starts
- **THEN** there is no `pushNotificationExecutor` bean and no notification listener is annotated `@Async`

