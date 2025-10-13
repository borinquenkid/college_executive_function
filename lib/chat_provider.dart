import 'package:flutter/material.dart';

class ChatMessage {
  final String author;
  final String message;

  ChatMessage({required this.author, required this.message});
}

class ChatProvider extends ChangeNotifier {
  final List<ChatMessage> _messages = [
    ChatMessage(author: 'User', message: 'What is the meaning of life?'),
    ChatMessage(author: 'AI', message: '42'),
  ];

  List<ChatMessage> get messages => _messages;

  void addMessage(ChatMessage message) {
    _messages.add(message);
    if (message.author == 'User') {
      _messages.add(ChatMessage(author: 'AI', message: 'Thinking...'));
      Future.delayed(const Duration(seconds: 1), () {
        _messages.removeLast();
        _messages.add(ChatMessage(author: 'AI', message: 'I am a bot.'));
        notifyListeners();
      });
    }
    notifyListeners();
  }
}
