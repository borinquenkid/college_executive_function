import 'package:college_executive_function/chat_provider.dart';
import 'package:college_executive_function/settings_page.dart';
import 'package:college_executive_function/sources_provider.dart';
import 'package:college_executive_function/studio_provider.dart';
import 'package:college_executive_function/theme.dart';
import 'package:college_executive_function/theme_provider.dart';
import 'package:flutter/material.dart';
import 'package:college_executive_function/sources_panel.dart';
import 'package:college_executive_function/chat_panel.dart';
import 'package:college_executive_function/studio_panel.dart';
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
        ChangeNotifierProvider(create: (context) => SourcesProvider()),
        ChangeNotifierProvider(create: (context) => StudioProvider()),
      ],
      child: Consumer<ThemeProvider>(
        builder: (context, themeProvider, child) {
          return MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: themeProvider.themeMode,
            home: const MyHomePage(),
            debugShowCheckedModeBanner: false,
          );
        },
      ),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool _isSourcesPanelVisible = false;
  bool _isStudioPanelVisible = false;

  @override
  Widget build(BuildContext context) {
    final isPhone = MediaQuery.of(context).size.width < 600;

    if (!isPhone) {
      // Original desktop layout
      return Scaffold(
        appBar: AppBar(
          title: const Text('College Executive Function'),
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

    // New phone layout
    return Scaffold(
      appBar: AppBar(
        title: const Text('College Executive Function'),
        leading: IconButton(
          icon: const Icon(Icons.source),
          onPressed: () {
            setState(() {
              _isSourcesPanelVisible = !_isSourcesPanelVisible;
            });
          },
        ),
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
      body: Stack(
        children: [
          const ChatPanel(),
          // Sources Panel
          if (_isSourcesPanelVisible)
            GestureDetector(
              onTap: () => setState(() => _isSourcesPanelVisible = false),
              child: Container(
                color: Colors.black.withOpacity(0.5),
              ),
            ),
          AnimatedPositioned(
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
            top: _isSourcesPanelVisible ? 0 : -MediaQuery.of(context).size.height,
            left: 0,
            right: 0,
            child: Material(
              elevation: 8,
              child: SizedBox(
                height: MediaQuery.of(context).size.height * 0.6,
                child: const SourcesPanel(),
              ),
            ),
          ),
          // Studio Panel
          if (_isStudioPanelVisible)
            GestureDetector(
              onTap: () => setState(() => _isStudioPanelVisible = false),
              child: Container(
                color: Colors.black.withOpacity(0.5),
              ),
            ),
          AnimatedPositioned(
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
            bottom: _isStudioPanelVisible ? 0 : -MediaQuery.of(context).size.height,
            left: 0,
            right: 0,
            child: Material(
              elevation: 8,
              child: SizedBox(
                height: MediaQuery.of(context).size.height * 0.6,
                child: const StudioPanel(),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          setState(() {
            _isStudioPanelVisible = !_isStudioPanelVisible;
          });
        },
        child: const Icon(Icons.edit_note),
      ),
    );
  }
}
