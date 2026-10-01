## ADDED Requirements

### Requirement: Private S3-compatible document storage
The system SHALL store CNH photos and driver-document files in a private S3-compatible bucket. It MUST persist an opaque object key rather than a public file URL, and bucket access MUST NOT permit anonymous object reads.

#### Scenario: Persisted document has no public URL
- **WHEN** a driver completes a document upload
- **THEN** the document record stores its private object key and no permanent public URL is exposed

### Requirement: Authorized signed upload and download URLs
The system SHALL issue expiring signed upload URLs only to the driver who owns the target resource or to an authorized Admin. It SHALL issue expiring signed download URLs only after the same ownership or Admin authorization check, and the caller MUST NOT select an object key outside their authorized upload scope.

#### Scenario: Owner uploads a CNH image
- **WHEN** an authenticated driver requests an upload URL for their CNH photo
- **THEN** the system returns a time-limited signed URL scoped to that driver's private object prefix

#### Scenario: Owner reads a document through a signed URL
- **WHEN** an authenticated driver requests their own document file
- **THEN** the system returns a time-limited signed download URL after ownership verification

#### Scenario: Other driver cannot access the file
- **WHEN** a driver requests an upload or download URL for another driver's document
- **THEN** the system denies the request and does not issue a signed URL

### Requirement: Environment-configured storage integration
The system SHALL obtain the S3-compatible endpoint, bucket, region, credentials, and signed-URL TTL from environment-backed configuration. Tests MUST use a fake storage adapter and MUST NOT call MinIO, AWS, or any external storage service.

#### Scenario: Test suite has no external storage dependency
- **WHEN** the storage upload or download flow is tested
- **THEN** the test uses a fake or mocked adapter and performs no network call to external storage
