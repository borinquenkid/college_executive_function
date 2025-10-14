import 'package:college_exeecutive_function/source_model.dart';
import 'package:flutter/material.dart';
import 'package:google_generative_ai/google_generative_ai.dart';

// FIXME: Add your API key here
const String _apiKey = 'YOUR_API_KEY';

class ChatMessage {
  final String author;
  final String message;

  ChatMessage({required this.author, required this.message});
}

class ChatProvider extends ChangeNotifier {
  final List<ChatMessage> _messages = [];

  List<ChatMessage> get messages => _messages;

  Future<void> addMessage(
    ChatMessage message,
    List<Source> sources,
  ) async {
    _messages.add(message);
    notifyListeners();

    if (message.author == 'User') {
      final model = GenerativeModel(model: 'gemini-pro', apiKey: _apiKey);

      String sourcesText = sources.map((s) => s.content).join('\n\n');
      String prompt =
          '''Based on the following sources:\n\n$sourcesText\n\nAnswer the following question: ${message.message}''';

      final content = [Content.text(prompt)];
      final response = await model.generateContent(content);

      _messages.add(ChatMessage(author: 'AI', message: response.text ?? ''));
      notifyListeners();
    }
  }
}
