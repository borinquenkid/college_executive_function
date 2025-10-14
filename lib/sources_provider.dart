import 'dart:io';

import 'package:college_exeecutive_function/source_model.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

class SourcesProvider extends ChangeNotifier {
  final List<Source> _sources = [];

  List<Source> get sources => _sources;

  Future<void> addSource() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['txt'],
    );

    if (result != null) {
      PlatformFile file = result.files.first;
      String content = await File(file.path!).readAsString();
      _sources.add(Source(name: file.name, content: content));
      notifyListeners();
    }
  }
}
