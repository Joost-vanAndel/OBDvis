# Tested Configurations

This page records real-world configurations on which OBDvis has been tested. It is a
community-maintained test log, not a guarantee that every model year, engine, ECU,
Android device, or ELM327-compatible adapter will behave identically.

## Vehicles

| Vehicle | Model year | Android device | Android version | OBD adapter | Test coverage | Notes |
|---|---:|---|---|---|---|---|
| Ford Focus 2.0 | 2007 | Xiaomi Mi 10T | Not recorded | No-name ELM327 | General app testing | No issues |
| Mazda MX-5 (NC1) 1.8 | 2007 | Xiaomi Mi 10T | Not recorded | No-name ELM327 | General app testing | Some issues with reading the wideband O2 sensor |
| Hyundai Ioniq | Not recorded | Xiaomi Mi 10T | Not recorded | No-name ELM327 | General app testing | Seems to work well after adding support for hybrid/electric vehicles |
| BMW 3 Series (E46) 325 | 2000 | Xiaomi Mi 10T | Not recorded | No-name ELM327 | General app testing | Extremely slow sensor polling |
| BMW 1 Series (F20) 116 | Not recorded | Xiaomi Mi 10T | Not recorded | No-name ELM327 | General app testing | These cars run their coolant temperature higher, which resulted in constant "engine overheating" findings |

## Add a Test Result

Contributions are welcome through a pull request or issue. Please include as much of the
following as you know:

- Vehicle make, model, generation, model year, engine, and fuel type.
- Android device and Android version.
- OBD adapter brand/model and connection type.
- Features exercised, such as connection, live PIDs, health checks, DTC reading or
  clearing, CSV export, and Android Auto.
- Outcome and any limitations, unsupported PIDs, or unusual behavior.

Do not include a VIN, license plate, precise location, Bluetooth address, or other personal
information. If a detail is unknown, use `Not recorded` rather than guessing.
