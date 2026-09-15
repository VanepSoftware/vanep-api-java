## Why

The dependent's address is the only address in the product a client still cannot set. `POST/PATCH /api/dependent` takes `AddressRequestDTO` — `cityToken`, `zipCode`, `street`, `number`, `complement` — and the app has no way to produce a `cityToken`: the CLIENT permission bundle grants the dependent CRUD plus `list_drivers`/`show_driver` (`DataSeeder.clientPermissions()`), so `GET /api/cities` answers 403, and that endpoint has no search-by-name anyway. The contract is not implementable from the client app.

Everywhere else the product already resolves a place server-side. `PUT /api/user/me/address` takes a `placeId` plus an optional `sessionToken`, calls `Place Details`, and builds the address from what Google returned; the `location-system` specs state the rule plainly — the system MUST NOT trust address components supplied by a client. The owned-address path (dependents and schools) is the one that still does, and it asks the client for a city it cannot know.

This blocks the dependents screen in `vanep-mobile` (issue #8), whose address phase is built and waiting on this change.

## What Changes

- **`AddressRequestDTO` becomes place-backed.** It carries `placeId`, `sessionToken`, `number` and `complement`, and no longer carries `cityToken`, `zipCode` or `street`. The system resolves the place through the existing `PlacesClient` + `LocationResolverService` and fills city, district, street, zip code and `google_place_id` from the result.
- **The owned address gains the district and the place id it never had.** `address.district_id` and `address.google_place_id` already exist in the schema and are filled by the personal-address path; the dependent and school paths left them null because the client-supplied payload had nothing to fill them with.
- **An update may amend without reselecting the place.** When the owner already has an address, an `address` object carrying only `number` and `complement` amends the stored row and keeps the resolved street. `placeId` replaces the whole address. On create, `placeId` is required whenever `address` is present.
- **Place resolution is extracted and shared.** `PersonalAddressService.replaceMyAddress` already resolves a place into an `AddressModel`; that block moves to a collaborator so the owned-address path uses the same code instead of a second copy (rule 6, rule 32).
- **BREAKING for schools too.** `AddressRequestDTO` is shared by `SchoolRequestDTO` and `SchoolUpdateRequestDTO`, so `POST/PATCH /api/schools` change shape with it. This is deliberate: the alternative is a second owned-address request DTO, which leaves the product with two contradictory ways to send an address and guarantees drift. School endpoints are permission-gated (`create_school`, `update_school`) and not reachable from the client app today.
- **No schema change.** `city_id`, `district_id`, `zip_code`, `street` and `google_place_id` all exist on `address`. No Flyway migration, so rule 2 is not in play.

## Capabilities

### New Capabilities

- `place-backed-owned-address`: how an owned address (dependent or school) is submitted as a Google place and resolved server-side — the request shape, the create-versus-amend rule, and the failures.

### Modified Capabilities

None in `openspec/specs/`. The requirement this supersedes ("City MUST be resolved by `cityToken`") lives in the `owned-address-model` change, which is complete but not yet synced into `openspec/specs/`. Syncing it is separate housekeeping and is not done here.

## Impact

**Changed DTO.** `br.com.vanep.address.dto.AddressRequestDTO` — new fields, and `@NotBlank` drops off everything, because `placeId` is required on create and optional on amend, which rule 10 puts in the service rather than in an annotation.

**New collaborator.** `br.com.vanep.address.service.AddressPlaceResolverService`, holding the resolve-and-apply step used by `PersonalAddressService` and `AddressService`.

**Changed services.** `AddressService.applyRequest` stops reading `cityToken` and resolves the place; `PersonalAddressService.replaceMyAddress` delegates its resolution block to the new collaborator, with no behaviour change. `DependentService` and `SchoolService` are untouched — they pass the DTO through.

**New message keys.** `address.place_required` for an `address` object with no `placeId` on an owner that has none yet. `location.address.street_required` and `location.place.not_found` already exist from the personal-address work.

**Tests.** `AddressServiceTest`, `DependentServiceTest`, `DependentControllerTest`, `SchoolServiceTest` and `SchoolControllerTest` build `AddressRequestDTO` by hand and all need the new shape. Every test that now reaches place resolution must stub `PlacesClient` with a committed fixture — rule 50 forbids a real call, and `application-test.properties` already points the Places base URL at an unroutable address.

**Consumer.** `vanep-mobile` branch `feat/8-dependent-address` is written against this contract and cannot open its PR until this merges.
