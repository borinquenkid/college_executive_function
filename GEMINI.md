# Project Mandates

## Testing Requirements

All business logic classes (Models, Repositories, Services, and Utilities) MUST have associated unit tests using the Kotest framework. Any new business logic introduced to the codebase must be accompanied by corresponding tests.

- **Mocking:** Use `MockK` for unit tests.
- **Network Testing:** Use Ktor `MockEngine` to verify API interactions.

UI components should have corresponding Compose tests where appropriate to verify state changes and user interactions.
