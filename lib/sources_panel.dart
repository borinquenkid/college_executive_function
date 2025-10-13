import 'package:flutter/material.dart';

class SourcesPanel extends StatelessWidget {
  const SourcesPanel({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: ListView(
        children: [
          const ListTile(
            leading: Icon(Icons.description),
            title: Text('Document 1.pdf'),
            subtitle: Text('12 pages'),
            trailing: Icon(Icons.more_vert),
          ),
          const ListTile(
            leading: Icon(Icons.description),
            title: Text('Source Code.dart'),
            subtitle: Text('256 lines'),
            trailing: Icon(Icons.more_vert),
          ),
          const ListTile(
            leading: Icon(Icons.link),
            title: Text('Website Article'),
            subtitle: Text('example.com'),
            trailing: Icon(Icons.more_vert),
          ),
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: ElevatedButton.icon(
              onPressed: () {},
              icon: const Icon(Icons.add),
              label: const Text('Add Source'),
            ),
          ),
        ],
      ),
    );
  }
}
