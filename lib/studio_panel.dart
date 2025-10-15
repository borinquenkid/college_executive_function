import 'dart:io';

import 'package:college_exeecutive_function/sources_provider.dart';
import 'package:college_exeecutive_function/studio_provider.dart';
import 'package:flutter/material.dart';
import 'package:google_generative_ai/google_generative_ai.dart';
import 'package:provider/provider.dart';

// FIXME: Add your API key here
const String _apiKey = 'YOUR_API_KEY';

class StudioPanel extends StatefulWidget {
  const StudioPanel({super.key});

  @override
  State<StudioPanel> createState() => _StudioPanelState();
}

class _StudioPanelState extends State<StudioPanel> {
  final TextEditingController _textController = TextEditingController();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final studioProvider = Provider.of<StudioProvider>(context);
    _textController.text = studioProvider.notes;
  }

  @override
  void dispose() {
    _textController.dispose();
    super.dispose();
  }

  Future<void> _generateContent(BuildContext context, String promptType) async {
    final sourcesProvider =
        Provider.of<SourcesProvider>(context, listen: false);
    final studioProvider = Provider.of<StudioProvider>(context, listen: false);

    final model = GenerativeModel(model: 'gemini-pro', apiKey: _apiKey);

    List<String> sourceContents = [];
    for (var source in sourcesProvider.allSources) {
      try {
        String content = await File(source.filePath).readAsString();
        sourceContents.add(content);
      } catch (e) {
        // Handle file read errors
        print('Error reading file: ${source.filePath}');
      }
    }

    String sourcesText = sourceContents.join('\n\n');
    String prompt =
        '''Based on the following sources:\n\n$sourcesText\n\nPlease generate a $promptType.''';

    try {
      final response = await model.generateContent([Content.text(prompt)]);
      _textController.text = response.text ?? 'Error: Could not generate content.';
      studioProvider.saveNotes(_textController.text);
    } catch (e) {
      _textController.text = 'Error: $e';
      studioProvider.saveNotes(_textController.text);
    }
  }

  @override
  Widget build(BuildContext context) {
    final studioProvider = Provider.of<StudioProvider>(context);

    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('STUDIO', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 16),
            Expanded(
              child: TextField(
                controller: _textController,
                maxLines: null,
                expands: true,
                decoration: const InputDecoration(
                  hintText: 'Your notes...',
                  border: OutlineInputBorder(),
                ),
                onChanged: (value) {
                  studioProvider.saveNotes(value);
                },
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: ListTile(
                leading: const Icon(Icons.summarize),
                title: const Text('Summary'),
                subtitle: const Text('Get a summary of your sources'),
                onTap: () => _generateContent(context, 'summary'),
              ),
            ),
            const SizedBox(height: 8),
            Card(
              child: ListTile(
                leading: const Icon(Icons.list_alt),
                title: const Text('Outline'),
                subtitle: const Text('Create an outline from your sources'),
                onTap: () => _generateContent(context, 'outline'),
              ),
            ),
            const SizedBox(height: 8),
            Card(
              child: ListTile(
                leading: const Icon(Icons.question_answer),
                title: const Text('Q&A'),
                subtitle: const Text('Generate questions and answers'),
                onTap: () => _generateContent(context, 'list of questions and answers'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
