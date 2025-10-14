import 'package:college_exeecutive_function/sources_provider.dart';
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
          child: Column(
            children: [
              Expanded(
                child: ListView.builder(
                  itemCount: sourcesProvider.sources.length,
                  itemBuilder: (context, index) {
                    final source = sourcesProvider.sources[index];
                    return ListTile(
                      leading: const Icon(Icons.description),
                      title: Text(source.name),
                      subtitle: Text('${source.content.length} characters'),
                      trailing: const Icon(Icons.more_vert),
                    );
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: ElevatedButton.icon(
                  onPressed: () {
                    sourcesProvider.addSource();
                  },
                  icon: const Icon(Icons.add),
                  label: const Text('Add Source'),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
