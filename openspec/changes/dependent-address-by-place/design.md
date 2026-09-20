## Context

Two address paths exist today and they disagree. `PersonalAddressService.replaceMyAddress` takes a `placeId`, calls `PlacesClient.findPlaceDetails`, pulls the street through `StreetAddressExtractor`, resolves the geography chain through `LocationResolverService.resolveAndPersist`, and writes city, district, street, zip code, number, complement and `google_place_id`. `AddressService.applyRequest`, used by dependents and schools, takes a `cityToken` plus a hand-typed street and zip code, looks the city up by token, and leaves `district_id` and `google_place_id` null.

The second path cannot be called by the client app: the CLIENT bundle has no `list_cities`, and `/api/cities` has no search. So the dependents screen in `vanep-mobile` has an address field with nothing to send.

Everything needed already exists. `AddressModel` has `district`, `google_place_id` and `zip_code` columns. `PlacesClient` caches and handles session tokens. `LocationResolverService` persists the country → state → city → district chain. Fixtures for `Place Details` are committed under `src/test/resources/fixtures/places/`, and `application-test.properties` pins the Places base URL to an unroutable address so rule 50 holds by configuration.

## Goals / Non-Goals

**Goals:**

- A client can set a dependent's address using only what the app can obtain: a Google `placeId`.
- One resolve-a-place-into-an-address implementation, shared by the personal and owned paths.
- Correcting a house number does not force the client to search for the street again.
- `PersonalAddressService` behaviour is byte-identical after the extraction, proved by its existing tests passing untouched.

**Non-Goals:**

- Changing `PUT /api/user/me/address`. It is already right; it only loses a duplicated block.
- Any schema change. Every column needed is already on `address`.
- Granting the CLIENT bundle `list_cities`, or adding search to `/api/cities`. This change removes the reason to want either.
- Syncing the completed `owned-address-model` change into `openspec/specs/`. Separate housekeeping.
- Backfilling `district_id` or `google_place_id` on address rows written by the old contract. They stay null until their owner edits the address.

## Decisions

### `AddressRequestDTO` changes shape rather than gaining a sibling

The DTO becomes `(placeId, sessionToken, number, complement)`. Schools ride on the same record and change with it.

Rejected alternative: a new `PlaceBackedAddressRequestDTO` for dependents, leaving schools on `cityToken`. It avoids touching school tests, and it is the wrong trade. The product would carry two contradictory owned-address contracts, and the next person to touch either would have to learn which owner uses which. Rule 6 says reuse or refactor before duplicating; rule 32 says remove duplication. School endpoints are permission-gated and unreachable from the client app, so the blast radius is test code and an admin surface, not a live client flow.

### `placeId` is validated in the service, not by an annotation

`placeId` is required when the owner has no address and optional when it does. Bean Validation cannot express "required depending on the state of another entity", and rule 10 already puts present-null versus present-blank versus uniqueness in the `@Service`. The DTO keeps `@Size` only.

Rejected alternative: `@NotBlank` on `placeId` plus a separate amend endpoint. That is a second endpoint to authorize, document and test, for a field the existing PATCH already carries.

### Amend keeps the resolved geography; a place replaces it

`applyRequest` branches on whether `placeId` has text. With text: resolve and overwrite every resolved column. Without: write `number` and `complement` only, and leave city, district, street, zip code and `google_place_id` alone. When the owner has no address and `placeId` is blank, 400 with `address.place_required`, because `address.city_id` is NOT NULL and a row with no geography cannot be searched.

Rejected alternative: treating an amend-only body as a full replace, nulling what it does not carry. It matches the old `applyRequest` semantics and would silently erase the resolved street on every number correction.

### The shared step is a collaborator, not a method on `AddressService`

`AddressPlaceResolverService` holds `applyPlace(address, placeId, sessionToken, number, complement)`. `PersonalAddressService` and `AddressService` both depend on it.

Rejected alternative: `PersonalAddressService` calling `AddressService`. `AddressService` depends on `DependentRepository` and `SchoolRepository` to enforce exclusive ownership; the personal path needs none of that and would drag both in. Rule 8 prefers extracting a rule into a class testable without the surrounding machinery.

### The caller's number wins over the place's

`Place Details` often returns a street number for a residential place, and the caller may be correcting it. The existing personal-address code already prefers the caller's value and falls back to the place; the extraction keeps exactly that precedence rather than inventing a new one.

## Phased delivery (rules 36–44)

| Phase | Contents | Depends on | Parallel with |
|----|----------|------------|---------------|
| 1 | `AddressPlaceResolverService` extracted from `PersonalAddressService`, behaviour unchanged | — | — |
| 2 | `AddressRequestDTO` place-backed; `AddressService` resolves through the collaborator; dependent and school tests updated | Phase 1 merged | — |

Phase 1 has zero internal dependencies and ships first (rule 38). Phase 2 consumes it. Neither phase adds a migration, so rule 42's migration step does not apply; within phase 2 the order is test → request DTO → service.

| Phase | New files | Changed files |
|----|----|----|
| 1 | 2 (service + its test) | 1 |
| 2 | 0 | ~8, mostly tests |

Both phases sit well inside the 600-line and 10-file caps (rule 41).

## Risks / Trade-offs

**School endpoints change shape in the same breath** → Deliberate, argued above. They are permission-gated and no shipped client calls them. The alternative is permanent drift between two owned-address contracts.

**Every test that saves an owned address now needs a `PlacesClient` stub** → Unavoidable once resolution is server-side, and rule 50 makes stubbing mandatory anyway. The `place-backed` fixtures are already committed and `PersonalAddressControllerTest` shows the pattern.

**Rows written under the old contract keep null `district_id` and `google_place_id`** → Accepted. No backfill is possible: the old rows never carried a `placeId`, so there is nothing to resolve them from. They fill in when their owner next edits the address.

**An amend-only body on an owner whose address was soft-deleted** → The owner's `address_id` is nulled on delete, so the owner counts as having no address and the request gets `address.place_required`, which is the correct answer rather than a resurrection.

## Open Questions

- Should `zipCode` remain readable on `AddressResponseDTO` when `Place Details` returns none for a place? It already can be null today; this change does not make it more or less likely, so the response contract stays as is.
