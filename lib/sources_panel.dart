import 'package:college_exeecutive_function/source_model.dart';
import 'package:college_exeecutive_function/sources_provider.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

class SourcesPanel extends StatelessWidget {
  const SourcesPanel({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<SourcesProvider>(
      builder: (context, sourcesProvider, child) {
        return Container(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('SOURCES', style: Theme.of(context).textTheme.headlineSmall),
              const SizedBox(height: 16),
              _buildSourceCard(
                context,
                category: SourceCategory.academicCalendar,
                title: 'Academic Calendar',
                source: sourcesProvider.getSourceForCategory(SourceCategory.academicCalendar),
              ),
              const SizedBox(height: 16),
              _buildSourceCard(
                context,
                category: SourceCategory.syllabus,
                title: 'Syllabus',
                source: sourcesProvider.getSourceForCategory(SourceCategory.syllabus),
              ),
              const SizedBox(height: 16),
              _buildSourceCard(
                context,
                category: SourceCategory.studentCalendar,
                title: 'Student Calendar',
                source: sourcesProvider.getSourceForCategory(SourceCategory.studentCalendar),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildSourceCard(BuildContext context, {required SourceCategory category, required String title, Source? source}) {
    final sourcesProvider = Provider.of<SourcesProvider>(context, listen: false);

    return Card(
      elevation: 2,
      child: InkWell(
        onTap: () async {
          if (source == null) {
            // 1. Pick the file first
            FilePickerResult? result = await FilePicker.platform.pickFiles(
              type: FileType.custom,
              allowedExtensions: ['txt', 'pdf'],
            );

            if (result != null) {
              PlatformFile file = result.files.first;

              // 2. Ask for a title
              final titleController = TextEditingController();
              final newTitle = await showDialog<String>(
                context: context,
                builder: (context) => AlertDialog(
                  title: Text('Add a title for \'${file.name}\''),
                  content: TextField(
                    controller: titleController,
                    decoration: const InputDecoration(hintText: "Enter title here"),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(),
                      child: const Text('Cancel'),
                    ),
                    TextButton(
                      onPressed: () {
                        Navigator.of(context).pop(titleController.text);
                      },
                      child: const Text('Save'),
                    ),
                  ],
                ),
              );

              if (newTitle != null && newTitle.isNotEmpty) {
                sourcesProvider.addSource(
                  category: category,
                  title: newTitle,
                  file: file,
                );
              }
            }
          }
        },
        child: Container(
          height: 120,
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              if (source == null)
                ...[
                  const Icon(Icons.add, size: 40, color: Colors.grey),
                  Text(title, style: Theme.of(context).textTheme.titleMedium),
                ]
              else
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        source.title,
                        style: Theme.of(context).textTheme.titleLarge,
                        textAlign: TextAlign.center,
                        overflow: TextOverflow.ellipsis,
                        maxLines: 2,
                      ),
                      Text(
                        source.name,
                        style: Theme.of(context).textTheme.bodySmall,
                        textAlign: TextAlign.center,
                        overflow: TextOverflow.ellipsis,
                        maxLines: 1,
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
