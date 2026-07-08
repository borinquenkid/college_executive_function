#!/usr/bin/env python3
import os
import re
import sys

# Regex pattern for contribution namespace validation:
# contributions/{state}/{name_of_college}/{academic_year_YYYY-YYYY}/{period}/{course_or_calendar}.{ext}
# - state: 2-letter state code, lowercase (e.g. 'mo')
# - name_of_college: lowercase with underscores (e.g. 'st_louis_community_college')
# - academic_year: YYYY-YYYY format starting in fall (e.g. '2025-2026')
# - period: lowercase with underscores (e.g. 'fall', 'spring', 'summer')
# - course_or_calendar: lowercase with underscores (e.g. 'academic_calendar')
# - ext: 'txt' (plain text), 'ics' (iCalendar), or 'pdf' (scanned/exported syllabi & calendars —
#   the actual corpus is 100% PDF; content is not locally text-scannable, see TEXT_SCANNABLE_EXTS)
PATH_PATTERN = re.compile(
    r'^contributions/([a-z]{2})/([a-z0-9_]+)/(\d{4}-\d{4})/([a-z0-9_]+)/([A-Za-z0-9_ -]+)\.(txt|ics|pdf)$'
)

# Extensions whose content can be decoded as text and scanned against POISON_PATTERNS.
# 'pdf' is intentionally excluded: it's binary, and this repo has no local PDF-text-extraction
# tooling (the app itself sends raw PDF bytes to Gemini's file API rather than parsing locally).
# Malicious/fabricated PDF content is defended against at runtime by the Confabulation Gate
# Protocol (GroundingGuardAIService: year/date/anchor/fact grounding), not by this CI check.
TEXT_SCANNABLE_EXTS = {'txt', 'ics'}

# Regex patterns for detecting poison/dangerous content
# Designed to block exploits, system/command injection, scripting, path traversal, and dangerous SQL statements
# without flagging normal academic calendar text like "last day to drop/add/delete courses".
POISON_PATTERNS = [
    # 1. Command/Shell Injection & Executable invocations
    r'\$\(.*\)',                           # System command substitution $(cmd)
    r'`[^`]+`',                            # Backtick command execution `cmd`
    r'\brm\s+-rf\b',                       # Recursive force delete
    r'\bsudo\s+',                          # Root privilege execution
    r'\b(chmod|chown)\s+',                 # Permission/ownership changes
    r'\b(curl|wget)\b',                    # Network retrievals
    r'\b(sh|bash|python|perl|eval|exec)\b\s+-[a-zA-Z0-9]', # Commands run with options
    r'\|\s*(sh|bash|python|perl|eval|exec)\b', # Piping into command shells

    # 2. Script & HTML/JS Injection
    r'<script\b[^>]*>',                    # HTML script tags
    r'\bjavascript:',                      # JavaScript URLs
    r'\bonload\s*=',                       # HTML onload events
    r'\bonerror\s*=',                      # HTML onerror events
    r'<\s*iframe\b',                       # IFrames
    r'<\s*(object|embed|applet)\b',        # Plugins/embedded objects

    # 3. Path Traversal
    r'\.\./',                              # Relative parent directory Unix/Mac
    r'\.\.\\',                             # Relative parent directory Windows

    # 4. Dangerous SQL injection (structured to avoid false positives on academic "drop/delete class")
    r'\bDROP\s+(TABLE|DATABASE|VIEW)\b',   # DROP command
    r'\bDELETE\s+FROM\b',                  # DELETE FROM command
    r'\bUNION\s+(ALL\s+)?SELECT\b',        # UNION SELECT query
    r'\bINSERT\s+INTO\b',                  # INSERT INTO command
    r'\bUPDATE\s+\w+\s+SET\b',             # UPDATE SET command

    # 5. Natural-language instruction-injection phrasing (prompt injection targeting the
    # downstream LLM extraction pipeline, rather than a technical exploit)
    r'\bignore\s+(all\s+|the\s+)?(previous|prior|above)\s+instructions\b',
    r'\bdisregard\s+(all\s+|the\s+)?(previous|prior|above)\b',
    r'\b(system|developer)\s+prompt\b',
    r'\byou\s+are\s+now\s+(a|an)\b',
    r'\bnew\s+instructions?\s*:',
]

def validate_file(filepath):
    # Enforce relative path formatting for pattern matching
    relative_path = os.path.relpath(filepath, '.')
    # Normalize path separators to forward slashes
    relative_path = relative_path.replace('\\', '/')

    # 1. Path Namespace validation
    match = PATH_PATTERN.match(relative_path)
    if not match:
        print(f"Error: Path '{relative_path}' violates the contributions namespace scheme.")
        print("Expected format: contributions/{state_code}/{name_of_college}/{academic_year}/{period}/{file_name}.{txt|ics|pdf}")
        print("Requirements:")
        print("  - state_code: 2-letter state code in lowercase (e.g., 'mo')")
        print("  - name_of_college: lowercase alphanumeric with underscores (e.g., 'st_louis_community_college')")
        print("  - academic_year: YYYY-YYYY format starting in fall (e.g., '2025-2026')")
        print("  - period: lowercase unique term (e.g., 'fall', 'spring')")
        print("  - file_name: alphanumeric with underscores/hyphens/spaces, extension one of .txt/.ics/.pdf (e.g., 'academic_calendar.txt')")
        return False

    state_code, name_of_college, academic_year, period, file_name, ext = match.groups()

    # 2. Verify Academic Year dates (starts in fall, end_year = start_year + 1)
    try:
        start_year, end_year = map(int, academic_year.split('-'))
        if end_year != start_year + 1:
            print(f"Error: Academic year '{academic_year}' in '{relative_path}' is invalid. The end year must be exactly start year + 1 (e.g., '2025-2026').")
            return False
    except ValueError:
         print(f"Error: Academic year '{academic_year}' must be in YYYY-YYYY format.")
         return False

    # 3. Size limit check (prevent denial of service/excessive resource ingestion)
    if os.path.getsize(filepath) > 5 * 1024 * 1024:  # 5MB limit
        print(f"Error: File '{relative_path}' is too large ({os.path.getsize(filepath)} bytes). Max allowed is 5MB.")
        return False

    # 4. Content-level scanning only applies to text-decodable formats (see TEXT_SCANNABLE_EXTS).
    # Binary formats (.pdf) are not scanned here; malicious/fabricated content in those files is
    # guarded against at runtime by the Confabulation Gate Protocol, not by this CI check.
    if ext not in TEXT_SCANNABLE_EXTS:
        return True

    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except UnicodeDecodeError:
        print(f"Error: File '{relative_path}' must be plain text encoded in UTF-8.")
        return False

    # 5. Scan for poison/dangerous regex patterns
    for pattern in POISON_PATTERNS:
        matches = re.findall(pattern, content, re.IGNORECASE)
        if matches:
            print(f"Error: File '{relative_path}' contains forbidden dangerous pattern/poison words: '{pattern}'")
            return False

    return True

def main():
    contributions_dir = 'contributions'
    if not os.path.exists(contributions_dir):
        print(f"Directory '{contributions_dir}' not found. Creating it...")
        os.makedirs(contributions_dir, exist_ok=True)
        return 0

    failed = False
    for root, dirs, files in os.walk(contributions_dir):
        for file in files:
            # Skip hidden files
            if file.startswith('.'):
                continue
            filepath = os.path.join(root, file)
            if not validate_file(filepath):
                failed = True

    if failed:
        print("Validation FAILED: One or more contributions are invalid or dangerous.")
        sys.exit(1)
    else:
        print("Validation SUCCESS: All contributions are clean and follow the namespace scheme.")
        sys.exit(0)

if __name__ == '__main__':
    main()
