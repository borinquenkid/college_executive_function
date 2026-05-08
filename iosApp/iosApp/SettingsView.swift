import SwiftUI
import ComposeApp

// 2. Settings View
struct SettingsView: View {
    let container: DependencyContainer
    @State private var apiKey: String = ""

    var body: some View {
        Form {
            Section(header: Text("AI Configuration")) {
                TextField("Gemini API Key", text: $apiKey)
                    .onChange(of: apiKey) { newValue in
                        container.settings.putString(key: "CEF_GEMINI_API_KEY", value: newValue)
                    }
            }
            Section {
                Button("Link Google Account") {
                    Task {
                        do {
                            try await container.googleAccountFlow.connect()
                        } catch {
                            print("Error connecting: \(error)")
                        }
                    }
                }
            }
        }
        .onAppear {
            apiKey = container.settings.getString(key: "CEF_GEMINI_API_KEY", defaultValue: "")
        }
    }
}
