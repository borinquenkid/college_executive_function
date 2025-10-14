import 'package:flutter/material.dart';

class StudioProvider extends ChangeNotifier {
  String _notes = '';

  String get notes => _notes;

  void saveNotes(String newNotes) {
    _notes = newNotes;
    notifyListeners();
  }
}
