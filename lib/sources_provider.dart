import 'package:college_executive_function/source_model.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';

class SourcesProvider extends ChangeNotifier {
  final List<Source> _academicCalendarSources = [];
  final List<Source> _syllabusSources = [];
  final List<Source> _studentCalendarSources = [];

  List<Source> get academicCalendarSources => _academicCalendarSources;
  List<Source> get syllabusSources => _syllabusSources;
  List<Source> get studentCalendarSources => _studentCalendarSources;

  List<Source> get allSources =>
      [..._academicCalendarSources, ..._syllabusSources, ..._studentCalendarSources];

  Source? getSourceForCategory(SourceCategory category) {
    switch (category) {
      case SourceCategory.academicCalendar:
        return _academicCalendarSources.isNotEmpty ? _academicCalendarSources.first : null;
      case SourceCategory.syllabus:
        return _syllabusSources.isNotEmpty ? _syllabusSources.first : null;
      case SourceCategory.studentCalendar:
        return _studentCalendarSources.isNotEmpty ? _studentCalendarSources.first : null;
    }
  }

  void addSource({
    required SourceCategory category,
    required String title,
    required PlatformFile file,
  }) {
    if (file.path != null) {
      final newSource = Source(
        name: file.name,
        filePath: file.path!,
        category: category,
        title: title,
      );

      switch (category) {
        case SourceCategory.academicCalendar:
          _academicCalendarSources.clear(); // Assuming one source per category for now
          _academicCalendarSources.add(newSource);
          break;
        case SourceCategory.syllabus:
          _syllabusSources.clear();
          _syllabusSources.add(newSource);
          break;
        case SourceCategory.studentCalendar:
          _studentCalendarSources.clear();
          _studentCalendarSources.add(newSource);
          break;
      }

      notifyListeners();
    }
  }
}
