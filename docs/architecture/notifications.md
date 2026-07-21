# Learner notifications

The first notification implementation uses Expo local notifications. It does not register device tokens or introduce remote push infrastructure.

Enabling a study reminder requests operating-system permission in context. The backend preference and OS permission are shown separately. A reminder is reported as scheduled only after permission is granted. Saving a new time cancels the previous identifier before creating one daily wall-clock schedule. Disabling reminders, logging out, or deleting the account cancels the learner-specific schedule stored in SecureStore.

The backend stores local reminder time plus an IANA timezone. On settings save, the device timezone becomes the preference. Expo's daily local trigger follows device wall-clock/DST behavior. When the authenticated app restores a session it reconciles the persisted preference with the device schedule. Denied permission is not repeatedly requested; the learner is offered the operating-system settings page.

Progress-summary and achievement preferences are persisted but are labelled as unavailable for delivery until server-triggered notification infrastructure is deliberately introduced. Marketing notifications default to absent.
