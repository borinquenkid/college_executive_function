import 'package:flutter/material.dart';

class StudioPanel extends StatelessWidget {
  const StudioPanel({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('STUDIO', style: Theme.of(context).textTheme.headlineSmall),
            const SizedBox(height: 16),
            Card(
              child: ListTile(
                leading: const Icon(Icons.summarize),
                title: const Text('Summary'),
                subtitle: const Text('Get a summary of your sources'),
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Generating Summary...')),
                  );
                },
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: ListTile(
                leading: const Icon(Icons.list_alt),
                title: const Text('Outline'),
                subtitle: const Text('Create an outline from your sources'),
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Generating Outline...')),
                  );
                },
              ),
            ),
            const SizedBox(height: 16),
            Card(
              child: ListTile(
                leading: const Icon(Icons.question_answer),
                title: const Text('Q&A'),
                subtitle: const Text('Ask questions about your sources'),
                onTap: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Generating Q&A...')),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
