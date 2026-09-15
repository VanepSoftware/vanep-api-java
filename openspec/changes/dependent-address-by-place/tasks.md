## 1. Phase 1 — shared place resolution (branch name: refactor/25-extract-address-place-resolver)

- [x] 1.1 Run `PersonalAddressControllerTest` and record it green, so the extraction has a before state
- [x] 1.2 Write `AddressPlaceResolverServiceTest` covering: a resolved place fills city, district, street, zip code and `google_place_id`; the caller's number wins over the place's; the place's number is used when the caller sends none; a place without a street returns 400 with `location.address.street_required`
- [x] 1.3 Add `br.com.vanep.address.service.AddressPlaceResolverService` with `applyPlace(address, placeId, sessionToken, number, complement)`, moved verbatim from `PersonalAddressService.replaceMyAddress`
- [x] 1.4 Make `PersonalAddressService` depend on the collaborator and delete its copy of the resolution block
- [x] 1.5 Confirm `PersonalAddressControllerTest` passes with no edits to its assertions
- [x] 1.6 Run `make lint` and `make test-coverage`
- [ ] 1.7 Open PR — adiado a pedido do desenvolvedor; branch commitada e enviada

## 2. Phase 2 — place-backed owned address (branch name: feat/25-owned-address-by-place)

- [x] 2.1 Update `AddressServiceTest` for the new request shape: a create resolves the place; an amend with only `number` keeps street, city, district and `google_place_id` and does not call `Place Details`; a `placeId` on an existing address replaces it in the same row; an `address` with no `placeId` on an owner with none returns 400 with `address.place_required`
- [x] 2.2 Update `DependentServiceTest` and `DependentControllerTest` for the new shape, including the named PATCH test that a single-field body leaves every other stored field unchanged (rule 16)
- [x] 2.3 Update `SchoolServiceTest` and `SchoolControllerTest` for the new shape
- [x] 2.4 Change `AddressRequestDTO` to `(placeId, sessionToken, number, complement)` with `@Size` only, dropping `cityToken`, `zipCode` and `street`
- [x] 2.5 Add the `address.place_required` key to `messages.properties` and `messages_pt_BR.properties`
- [x] 2.6 Rewrite `AddressService.applyRequest` to branch on `placeId`: resolve and overwrite, or amend `number` and `complement` only; reject a blank `placeId` on an owner with no address
- [x] 2.7 Delete `AddressService.requireCityByToken` and its now-unused `CityRepository` dependency if nothing else uses them
- [x] 2.8 Confirm `DependentService` and `SchoolService` need no change, since they pass the DTO through
- [x] 2.9 Run `make lint` and `make test-coverage`
- [ ] 2.10 Open PR — adiado a pedido do desenvolvedor; branch commitada e enviada
