# Amare development

- The user directs design and product behavior. Ask before implementing ambiguous requirements or changing inventory/payment semantics.
- All generated code and documentation belong in this repository.
- After any code change, run the relevant automated tests and a complete Android build before telling the user it is ready. Run Android lint for every APK delivery.
- Beta is admin-only, offline, Spanish, HNL centavos. Never put customer data, backups, credentials or signing keys in Git.
- Stock is physical stock; reservations reduce availability but never physical stock. Delivered orders deduct physical stock exactly once. Purchases increase stock only when received, exactly once.
- Cancellation with paid amounts requires explicit confirmation that the actual refund was made. The app records payment events; it does not move money.
- Keep operations transactional, money in integer centavos, and preserve historical price/cost snapshots.
- Target Android 8+ with no required Google Play services. Do not claim device compatibility beyond verified environments.
- Keep the Store interface boundary ready for future remote storage; do not add a server or Supabase before the user requests it.
- Keep the APK signing identity stable across delivered updates. Signing material stays outside the repository. A private backup named `Amare-signing-backup.zip` contains the persistent beta key and credentials; recover it privately before signing subsequent releases.
- `dist/Amare-*.apk` is the user-installable beta. CI debug artifacts use another signing identity and should not be installed over it.
