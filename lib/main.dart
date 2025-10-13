import 'package:college_exeecutive_function/chat_provider.dart';
import 'package:college_exeecutive_function/settings_page.dart';
import 'package:college_exeecutive_function/theme.dart';
import 'package:college_exeecutive_function/theme_provider.dart';
import 'package:flutter/material.dart';
import 'package:college_exeecutive_function/sources_panel.dart';
import 'package:college_exeecutive_function/chat_panel.dart';
import 'package:college_exeecutive_function/studio_panel.dart';
import 'package:provider/provider.dart';

void main() {
  runApp(const MainApp());
}

class MainApp extends StatelessWidget {
  const MainApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (context) => ChatProvider()),
        ChangeNotifierProvider(create: (context) => ThemeProvider()),
      ],
      child: Consumer<ThemeProvider>(
        builder: (context, themeProvider, child) {
          return MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: themeProvider.themeMode,
            home: const MyHomePage(),
          );
        },
      ),
    );
  }
}

class MyHomePage extends StatelessWidget {
  const MyHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('NotebookLM Clone'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (context) => const SettingsPage()),
              );
            },
          ),
        ],
      ),
      body: const Row(
        children: [
          Expanded(flex: 1, child: SourcesPanel()),
          Expanded(flex: 2, child: ChatPanel()),
          Expanded(flex: 1, child: StudioPanel()),
        ],
      ),
    );
  }
}
