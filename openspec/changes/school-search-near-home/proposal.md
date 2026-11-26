## Why

School autocomplete needs a reliable nearby point before it can bias Google Places results. The API currently identifies a city but exposes no coordinates, so the mobile app falls back to the device or IP even when an address is known.

## What Changes

- Add nullable latitude and longitude to the city catalog.
- Load one official 2022 IBGE locality point for every city in the API catalog.
- Expose the city point in city and address responses.
- Preserve null coordinates for non-IBGE or incomplete catalog entries.

## Capabilities

### New Capabilities

- `school-search-near-home`: city coordinates suitable for a soft nearby-school search bias.

### Modified Capabilities

- `city`: responses include the municipal-seat coordinates.
- `address`: address responses include their city's coordinates.

## Impact

- Database: two nullable numeric columns and a one-time data load.
- API: additive nullable response fields only; no request changes.
- Seed: new cities receive coordinates from the same committed IBGE catalog.
