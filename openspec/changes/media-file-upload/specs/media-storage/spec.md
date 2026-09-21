## ADDED Requirements

### Requirement: Storage access goes through one abstraction

All binary storage access MUST go through a `StorageService` interface exposing upload, open and delete. `LocalStorageService` MUST be the implementation for now.

No controller, domain service or model MAY reference the filesystem directly. The storage root, the active provider and the size limits MUST come from environment variables, never hardcoded.

#### Scenario: A domain service never touches the filesystem

- **WHEN** a feature stores or reads a file
- **THEN** it does so through `StorageService`
- **AND** it holds no reference to a filesystem path

#### Scenario: The storage root comes from the environment

- **WHEN** the application starts
- **THEN** the storage root is read from configuration
- **AND** starting without it fails explicitly instead of falling back to a default path

### Requirement: Media metadata lives in its own table, keyed by object key

A Flyway migration MUST create `media_file` holding an opaque `token`, the owner (`owner_type`, `owner_id`), the `purpose`, the `provider`, the `object_key`, the mime type, the size, the original name, the visibility and a nullable `deleted_at`.

The table MUST store the **object key**, never a full URL: a URL freezes the host inside the database and turns every row into garbage the day storage moves. The readable URL MUST be derived at read time from the row's `provider`.

Each row MUST carry its own `provider`, so files can be migrated in batches while the system serves from both places.

#### Scenario: A stored file is addressed by an opaque token

- **WHEN** a file is uploaded
- **THEN** the response carries an opaque token
- **AND** no numeric id is exposed

#### Scenario: The row survives a change of storage location

- **WHEN** a file's bytes are moved to another provider under the same object key
- **AND** the row's `provider` is updated
- **THEN** the file is still readable through the same token

#### Scenario: Two providers coexist during a migration

- **WHEN** some rows carry one provider and some another
- **THEN** each file is served from the provider recorded on its own row

### Requirement: Upload validates the real content type and the size

`POST /api/media` MUST accept `multipart/form-data` with the file, the owner and the purpose, and MUST return the created token, the mime type and the size.

The content type MUST be determined from the **file's own bytes**, not from its extension or from the request's declared `Content-Type`, and MUST be checked against what the `purpose` allows. A file whose real type is not allowed MUST be rejected with HTTP 400.

A file larger than the configured limit MUST be rejected with HTTP 413, and MUST NOT be stored.

#### Scenario: A valid image is stored

- **WHEN** an authenticated caller uploads an image for a photo purpose
- **THEN** the system returns HTTP 201
- **AND** a `media_file` row exists pointing at the stored object

#### Scenario: A file lying about its type is rejected

- **WHEN** a caller uploads a file named with an image extension whose bytes are not an image
- **THEN** the system returns HTTP 400
- **AND** nothing is written to storage

#### Scenario: An oversized file is rejected

- **WHEN** a caller uploads a file larger than the configured limit
- **THEN** the system returns HTTP 413
- **AND** nothing is written to storage

#### Scenario: An unauthenticated upload is refused

- **WHEN** a request reaches the upload endpoint without a bearer token
- **THEN** the system returns HTTP 401

### Requirement: The object key is derived by the server, never received

The stored path MUST be built from the owner type, the owner's opaque token, the purpose, a server-generated media token and a suffix chosen from the **detected** mime type.

No part of the client's input MAY reach the stored path. The uploaded file name MUST be kept as metadata only.

Independently of that, `StorageService` MUST reject any object key that resolves outside the configured root, as defense in depth.

#### Scenario: The uploaded file name does not reach the path

- **WHEN** a file is uploaded with any name whatsoever
- **THEN** the stored path is built from the owner, the purpose and generated tokens
- **AND** the original name is kept only as metadata

#### Scenario: A key escaping the root is refused

- **WHEN** an object key that resolves outside the storage root reaches `StorageService`
- **THEN** the operation is refused
- **AND** nothing is written outside the root

### Requirement: Bytes are served by the application, with authorization

The stored file MUST NOT be reachable except through the application. `GET /api/media/{token}/download` MUST resolve and authorize **before** any byte is read from storage.

A `PRIVATE` file MUST be readable only by its owner or by a caller holding `show_media`. A caller who is neither MUST receive HTTP 403 with an **empty body** — a refusal MUST NOT carry any part of the file.

#### Scenario: The owner downloads their own file

- **WHEN** the owner requests the download of their own file
- **THEN** the system returns HTTP 200 with the bytes

#### Scenario: An admin downloads someone else's file

- **WHEN** a caller holding `show_media` requests the download of a private file they do not own
- **THEN** the system returns HTTP 200 with the bytes

#### Scenario: An unrelated user is refused without receiving any content

- **WHEN** a user who neither owns the file nor holds `show_media` requests its download
- **THEN** the system returns HTTP 403
- **AND** the response body is empty

#### Scenario: A removed file is no longer served

- **WHEN** a file has been deleted
- **AND** its download is requested
- **THEN** the system returns HTTP 404

### Requirement: The download address does not change when storage moves

`GET /api/media/{token}/download` MUST be the only address clients use, for every provider. Clients MUST NOT receive a provider-native URL.

For a local file the endpoint MUST stream the bytes; for a remote one it MUST redirect to a short-lived signed URL. Either way the client uses the same address and needs no change when files move between providers, including while some files are on each side.

#### Scenario: The address is the same regardless of provider

- **WHEN** a file is served from one provider
- **AND** an equivalent file is served from another
- **THEN** both are requested through the same endpoint shape
- **AND** neither response requires the client to handle a provider-specific URL

### Requirement: Deleting the record deletes the bytes

`DELETE /api/media/{token}` MUST soft delete the row **and** remove the stored object.

Removing the row alone MUST NOT happen: it is what makes today's URL columns accumulate files nobody can account for, on a disk that is already the constraint.

#### Scenario: Deleting removes both the row and the object

- **WHEN** a caller authorized to delete removes a file
- **THEN** the row is soft deleted
- **AND** the stored object no longer exists

#### Scenario: Deleting is refused for an unrelated user

- **WHEN** a user who does not own the file deletes it
- **THEN** the system returns HTTP 403
- **AND** the stored object still exists
