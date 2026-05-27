#!/usr/bin/env python3
"""
generate_content.py
Re-generates content.js by embedding all markdown and Java source files.
Run from the LLD/ directory.
"""

import os
import json

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Folders to scan for markdown
MD_DIRS = ['docs', 'MySQL', 'HLD', 'DSA']

# Java source root
JAVA_ROOT = os.path.join(BASE_DIR, 'src', 'main', 'java')

entries = {}

# --- Embed Markdown files ---
for folder in MD_DIRS:
    folder_path = os.path.join(BASE_DIR, folder)
    if not os.path.isdir(folder_path):
        continue
    for root, dirs, files in os.walk(folder_path):
        # skip node_modules and hidden dirs
        dirs[:] = [d for d in dirs if not d.startswith('.')]
        for fname in sorted(files):
            if fname.endswith('.md'):
                full_path = os.path.join(root, fname)
                rel_key = os.path.relpath(full_path, BASE_DIR).replace(os.sep, '/')
                with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
                    entries[rel_key] = f.read()

# --- Embed Java source files ---
for root, dirs, files in os.walk(JAVA_ROOT):
    dirs[:] = [d for d in dirs if not d.startswith('.')]
    for fname in sorted(files):
        if fname.endswith('.java'):
            full_path = os.path.join(root, fname)
            # Key: src/com/lld/...
            rel_key = 'src/' + os.path.relpath(full_path, os.path.join(BASE_DIR, 'src', 'main', 'java')).replace(os.sep, '/')
            with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
                entries[rel_key] = f.read()

# --- Write content.js ---
out_path = os.path.join(BASE_DIR, 'content.js')
with open(out_path, 'w', encoding='utf-8') as out:
    out.write('/* AUTO-GENERATED — do not edit manually. Run generate_content.py to update. */\n')
    out.write('const SITE_CONTENT = {\n')
    for key, value in sorted(entries.items()):
        out.write(f'  {json.dumps(key)}: {json.dumps(value)},\n')
    out.write('};\n')

print(f"Done! Embedded {len(entries)} files into content.js")
md_count  = sum(1 for k in entries if k.endswith('.md'))
java_count = sum(1 for k in entries if k.endswith('.java'))
print(f"  Markdown: {md_count}  |  Java: {java_count}")
