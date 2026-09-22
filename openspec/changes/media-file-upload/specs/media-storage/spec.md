## ADDED Requirements

### Requirement: Storage access goes through one abstraction

All binary storage access MUST go through a `StorageService` interface exposing upload, open and delete. `LocalStorageService` MUST be the implementation for now.

No controller, domain service or model MAY reference the filesystem directly. The storage root, the active provider and the size limits MUST come from environment variables, with no silent default.

#### Scenario: A domain service never touches the filesystem

- **WHEN** a feature stores or reads a file
- **THEN** it does so through `StorageService`
- **AND** it holds no reference to a filesystem path

#### Scenario: Starting without a storage root fails loudly

- **WHEN** the application starts with no storage root configured
- **THEN** startup fails explicitly instead of falling back to a default path

### Requirement: The owner points at the media

A media row MUST NOT carry its owner. Each owning table MUST hold a nullable foreign key to `media_file`, one per slot, replacing the `varchar` URL column it has today.

This direction is required so that reading an owner returns its file in a single request, and so the database can enforce the relationship. A polymorphic owner column accepts no foreign key and cannot stop a media row from pointing at a deleted owner.

#### Scenario: Reading an owner returns its file in one request

- **WHEN** a client reads a driver that has a photo
- **THEN** the response carries the photo's URL
- **AND** no further API call is needed to learn it

#### Scenario: The relationship is enforced by the database

- **WHEN** an owner references a media row
- **THEN** that reference is a foreign key
- **AND** a reference to a non-existent media row is rejected

#### Scenario: An owner without a file is a valid state

- **WHEN** an owner has no file for a slot
- **THEN** its reference is null
- **AND** the response carries no URL for that slot

### Requirement: Upload is addressed by owner, never by a generic filter

Each slot MUST have its own upload endpoint under the owner's resource, taking `multipart/form-data`. There MUST NOT be a generic media endpoint addressed by an owner type and token.

The owner type MUST come from the route, never from the request body: the server already knows it.

#### Scenario: A photo is uploaded through its owner

- **WHEN** an authorized caller posts an image to a driver's photo endpoint
- **THEN** the system returns HTTP 201
- **AND** the driver's photo reference points at the stored media

#### Scenario: Replacing a file releases the previous one

- **WHEN** an owner that already has a file receives a new one for the same slot
- **THEN** the reference points at the new media
- **AND** the previous media and its bytes are removed

#### Scenario: An unauthenticated upload is refused

- **WHEN** a request reaches an upload endpoint without a bearer token
- **THEN** the system returns HTTP 401

### Requirement: Upload validates the real content type and the size

The content type MUST be determined from the **file's own bytes**, not from its extension or the request's declared type, and MUST be checked against what the slot allows. A file whose real type is not allowed MUST be rejected with HTTP 400.

A file larger than the configured limit MUST be rejected with HTTP 413. Neither rejection MAY leave a row or a stored object behind.

#### Scenario: A file lying about its type is rejected

- **WHEN** a caller uploads a file named with an image extension whose bytes are not an image
- **THEN** the system returns HTTP 400
- **AND** nothing is written to storage

#### Scenario: An oversized file is rejected

- **WHEN** a caller uploads a file larger than the configured limit
- **THEN** the system returns HTTP 413
- **AND** nothing is written to storage

### Requirement: The stored path is derived by the server

The object key MUST be built from the owner type taken from the route, the owner's opaque token, the slot, a server-generated media token and a suffix chosen from the detected mime type.

No part of the client's input MAY reach the stored path. The uploaded file name MUST be kept as metadata only. Independently, `StorageService` MUST reject any object key that resolves outside the configured root.

#### Scenario: The uploaded file name does not reach the path

- **WHEN** a file is uploaded with any name whatsoever
- **THEN** the stored path is built from the owner, the slot and generated tokens
- **AND** the original name is kept only as metadata

#### Scenario: A key escaping the root is refused

- **WHEN** an object key that resolves outside the storage root reaches `StorageService`
- **THEN** the operation is refused
- **AND** nothing is written outside the root

### Requirement: Bytes are served by the application, through the owner's own route

The stored file MUST NOT be reachable except through the application, and MUST be downloaded from the owner's own route, never from a generic media address.

Downloading MUST resolve and authorize **before** any byte is read from storage. Authorization MUST reuse the owner's existing ownership rule.

The decision between streaming the bytes and redirecting to a short-lived signed URL MUST live in a single shared component, so that changing provider touches one place while every route stays unchanged.

A private file MUST be readable only by its owner or by a caller holding the media read permission. A refusal MUST return HTTP 403 with an **empty body**.

#### Scenario: The owner downloads their own file

- **WHEN** the owner requests the download of their own file
- **THEN** the system returns HTTP 200 with the bytes

#### Scenario: An unrelated user is refused without receiving any content

- **WHEN** a user who neither owns the file nor holds the read permission requests it
- **THEN** the system returns HTTP 403
- **AND** the response body is empty

#### Scenario: Changing provider does not change any route

- **WHEN** a file moves to another provider
- **THEN** it is downloaded from the same owner route as before
- **AND** only the shared responding component behaves differently

#### Scenario: There is no generic media address

- **WHEN** a client needs a file
- **THEN** it requests it from the owner's route
- **AND** no endpoint serves media addressed by an owner type and token

### Requirement: Media metadata is keyed by object key, not by URL

`media_file` MUST store the object key and the provider that serves it, never a full URL. A URL freezes the host inside the database and turns every row into garbage the day storage moves.

Each row MUST carry its own provider, so files can be migrated in batches while the system serves from both places.

#### Scenario: The row survives a change of storage location

- **WHEN** a file's bytes are moved to another provider under the same object key
- **AND** the row's provider is updated
- **THEN** the file is still readable at the same address

#### Scenario: Two providers coexist during a migration

- **WHEN** some rows carry one provider and some another
- **THEN** each file is served from the provider recorded on its own row

### Requirement: The write path for a file is the upload endpoint alone

The request DTOs that accept a URL string for these fields MUST stop accepting it. A file is set by uploading it, never by sending a string.

Response DTOs MUST keep their current field name and type, now carrying a working URL, so that readers do not break.

#### Scenario: A URL string is no longer accepted on update

- **WHEN** a caller sends a file URL as a string on an update request
- **THEN** the field is not part of the contract

#### Scenario: The response shape is unchanged

- **WHEN** an owner with a file is returned
- **THEN** the file field has the same name and type it had before
- **AND** it carries a URL that resolves
