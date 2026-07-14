# Privacy

OBDvis is designed to run locally.

## Data Processing

The active Android app reads OBD-II sensor values, DTCs, Bluetooth adapter details, and
linear acceleration sensor values needed for the in-app experience. This data is processed
on the device for dashboards, health checks, charts, Android Auto templates, notifications,
and post-drive summaries.

The active app does not upload vehicle data, DTCs, Bluetooth device information, location,
or driving summaries to a remote service.

## Permissions

- Bluetooth permissions are used to discover and connect to paired ELM327 adapters.
- Fine location is requested only on Android versions where Bluetooth scanning requires it.
- Notification permission is used for optional health notifications.
- Android Auto metadata is used to expose the read-only car app templates.

## Local Exports

CSV export is user-initiated. Exported files are written only to the location selected by
the user through Android's document picker.

## External DTC Searches

Tapping a diagnostic trouble code opens the device's search provider or web browser with
that code as the search query. This is user-initiated and may send the code to the selected
search provider under that provider's privacy policy. OBDvis itself does not make the
network request.
