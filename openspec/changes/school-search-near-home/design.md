## Context

The mobile school search needs a point for `locationBias` and `origin`. Both the dependent address and the client's personal address already reference the city catalog, but city rows have no coordinates.

## Goals / Non-Goals

Goals:

- Supply a stable nearby point for all 5,571 entries in the API city catalog.
- Make existing city and address responses directly usable by the mobile client.
- Keep the change backward compatible.

Non-goals:

- Store a user's precise coordinates.
- Restrict results to a radius or municipality.
- Call Google during migration, seed, or tests.

## Decisions

### Use the IBGE locality point for the municipal seat

The source is the official IBGE `Localidades_do_Brasil/2022` GeoPackage. Rows with `CT_LOCALIDADE = Cidade` provide `LAT_LOCALIDADE` and `LONG_LOCALIDADE`. The source contains 5,596 such rows because 26 state capitals appear both as a municipal seat and a state capital; those pairs have the same point. They produce 5,570 distinct codes. The API's existing IBGE dump also contains Fernando de Noronha (`2605459`), classified by this source as `Distrito Estadual de Fernando de Noronha`; its official locality point is included, producing exactly 5,571 catalog rows.

The municipal-seat point is preferred over a polygon centroid because it represents the populated city center and is a better origin for nearby school ranking. Source: `https://geoftp.ibge.gov.br/organizacao_do_territorio/estrutura_territorial/localidades/Localidades_do_Brasil/2022/Localidades_Brasil_gpkg.zip`.

The IBGE directory identifies the files as public. Brazilian open-data guidance defines open government data as freely available for use and reuse, requiring source attribution; this change preserves attribution here and in the database column descriptions. Reference: `https://www.gov.br/acessoainformacao/pt-br/lai-para-sic/transparencia-ativa/copy_of_guia-de-transparencia-ativa/textos-complementares/formato-aberto`.

### Keep coordinates nullable and paired

Latitude and longitude are nullable so custom catalog rows remain valid. A database check requires both values together and validates latitude in `[-90, 90]` and longitude in `[-180, 180]`.

### Reuse one versioned catalog

Flyway migration `V72` adds the columns and `V73` updates existing rows by `ibge_code` from SQL generated from the same CSV. `CitySeeder` reads the same CSV when it creates cities in a fresh database, because seed runs after Flyway and the city rows do not yet exist during migration.

### Additive response fields

`CityResponseDTO`, `AddressResponseDTO`, and `PersonalAddressResponseDTO` expose `cityLatitude` and `cityLongitude` as nullable decimal numbers. Existing consumers can ignore them.

## Risks / Trade-offs

- A city-center point is approximate, particularly in large municipalities. The mobile client therefore uses a soft 50 km bias, never a hard restriction.
- The committed catalog adds generated data to the repository, but makes runtime deterministic and avoids external requests.
