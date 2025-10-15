enum SourceCategory {
  academicCalendar,
  syllabus,
  studentCalendar,
}

class Source {
  final String name; // Original file name
  final String filePath;
  final SourceCategory category;
  String title; // User-provided title

  Source({
    required this.name,
    required this.filePath,
    required this.category,
    required this.title,
  });
}
