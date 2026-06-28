# Constitution

Rules that MUST be followed in this codebase.

1. **Never hardcode URLs or secrets in source code.** All URLs, hosts, ports, keys, and credentials must come from environment variables or `application.properties` referencing env vars. No magic strings for connection endpoints.
