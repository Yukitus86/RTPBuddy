# -*- coding: utf-8 -*-
"""Make a new Fabric mod from the template, in a folder of its own.

    python new-mod.py --to <folder> --id <modid> [--name "<Name>"] [--package <pkg>]

Example:

    python new-mod.py --to ../RTPTracker --id rtptracker

The template is never built where it lies - it is a source folder in RTPBuddy's
repository, and the second mod is supposed to live somewhere else and have its
own git history. This copies it out, renames everything in it, brings the gradle
wrapper along from RTPBuddy, and drops RTPBuddy's jar into libs/ if it is built.
"""
from __future__ import print_function

import io
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RTPBUDDY_ROOT = os.path.dirname(HERE)
TEMPLATE = os.path.join(HERE, 'template')

# What the template calls itself, in every spelling that appears in its files.
OLD_ID = 'rtpcompanion'
OLD_PACKAGE = 'dev.rtpcompanion'
OLD_NAME = 'RTP Companion'
OLD_CLASS_PREFIX = 'RTPCompanion'

TEXT_SUFFIXES = ('.java', '.json', '.gradle', '.properties', '.md', '.gitignore')

WRAPPER_FILES = [
    'gradlew',
    'gradlew.bat',
    os.path.join('gradle', 'wrapper', 'gradle-wrapper.jar'),
    os.path.join('gradle', 'wrapper', 'gradle-wrapper.properties'),
]


def die(message):
    print(message)
    sys.exit(1)


def parse(argv):
    options = {'--to': None, '--id': None, '--name': None, '--package': None}
    i = 1
    while i < len(argv):
        key = argv[i]
        if key not in options:
            die('unknown option %s\n\n%s' % (key, __doc__))
        if i + 1 >= len(argv):
            die('%s needs a value' % key)
        options[key] = argv[i + 1]
        i += 2
    if not options['--to'] or not options['--id']:
        die(__doc__)
    return options


def class_prefix(mod_id):
    """rtptracker -> RtpTracker is wrong more often than it is right, so the
    class prefix is simply the id with a capital first letter unless the caller
    says otherwise. The template's own classes keep working either way."""
    return mod_id[:1].upper() + mod_id[1:]


def main(argv):
    options = parse(argv)

    mod_id = options['--id']
    if not re.match(r'^[a-z][a-z0-9_-]{1,63}$', mod_id):
        die('mod id must be lower case, start with a letter, and hold only '
            'a-z 0-9 _ - : got %r' % mod_id)

    name = options['--name'] or mod_id
    package = options['--package'] or ('dev.' + mod_id.replace('-', ''))
    if not re.match(r'^[a-z][a-z0-9_.]*$', package):
        die('package must be a lower case java package: got %r' % package)
    new_class_prefix = class_prefix(re.sub(r'[^A-Za-z0-9]', '', mod_id))

    target = os.path.abspath(options['--to'])
    if os.path.exists(target) and os.listdir(target):
        die('target exists and is not empty: %s' % target)

    # 1. the template itself
    shutil.copytree(TEMPLATE, target, dirs_exist_ok=True)

    # 2. the java package folders
    old_dir = os.path.join(target, 'src', 'main', 'java', *OLD_PACKAGE.split('.'))
    new_dir = os.path.join(target, 'src', 'main', 'java', *package.split('.'))
    if old_dir != new_dir:
        os.makedirs(os.path.dirname(new_dir), exist_ok=True)
        shutil.move(old_dir, new_dir)
        # dev/ is left behind empty when the group changes
        prune(os.path.join(target, 'src', 'main', 'java'), new_dir)

    # 3. the assets folder, which must be named after the mod id
    old_assets = os.path.join(target, 'src', 'main', 'resources', 'assets', OLD_ID)
    new_assets = os.path.join(target, 'src', 'main', 'resources', 'assets', mod_id)
    if old_assets != new_assets:
        shutil.move(old_assets, new_assets)

    # 4. every spelling inside the files, longest first so the package name is
    #    replaced before the bare id it contains
    replacements = [
        (OLD_PACKAGE, package),
        (OLD_NAME, name),
        (OLD_CLASS_PREFIX, new_class_prefix),
        (OLD_ID, mod_id),
    ]
    touched = 0
    for root, _dirs, files in os.walk(target):
        for filename in files:
            if not filename.endswith(TEXT_SUFFIXES):
                continue
            path = os.path.join(root, filename)
            text = io.open(path, encoding='utf-8').read()
            replaced = text
            for old, new in replacements:
                replaced = replaced.replace(old, new)
            if replaced != text:
                io.open(path, 'w', encoding='utf-8', newline='\n').write(replaced)
                touched += 1

    # 5. rename the class files themselves
    for root, _dirs, files in os.walk(new_dir):
        for filename in files:
            if OLD_CLASS_PREFIX in filename:
                os.rename(os.path.join(root, filename),
                          os.path.join(root, filename.replace(OLD_CLASS_PREFIX,
                                                              new_class_prefix)))

    # 6. the gradle wrapper, so the new project builds without a gradle install
    for relative in WRAPPER_FILES:
        source = os.path.join(RTPBUDDY_ROOT, relative)
        if not os.path.isfile(source):
            print('wrapper file missing, skipped: %s' % source)
            continue
        destination = os.path.join(target, relative)
        os.makedirs(os.path.dirname(destination), exist_ok=True)
        shutil.copy2(source, destination)

    # 7. RTPBuddy's jar, if it happens to be built already
    version = read_property(os.path.join(target, 'gradle.properties'), 'rtpbuddy_version')
    jar = os.path.join(RTPBUDDY_ROOT, 'build', 'libs', 'rtpbuddy-%s.jar' % version)
    libs = os.path.join(target, 'libs')
    if os.path.isfile(jar):
        shutil.copy2(jar, os.path.join(libs, os.path.basename(jar)))
        jar_note = 'libs/%s' % os.path.basename(jar)
    else:
        jar_note = ('MISSING - build RTPBuddy, then run\n'
                    '      python %s %s'
                    % (os.path.join(HERE, 'install-rtpbuddy-jar.py'), target))

    print('')
    print('  mod id     %s' % mod_id)
    print('  name       %s' % name)
    print('  package    %s' % package)
    print('  folder     %s' % target)
    print('  files      %d rewritten' % touched)
    print('  rtpbuddy   %s' % jar_note)
    print('')
    print('  next:  cd %s' % target)
    print('         git init')
    print('         gradlew build')
    print('')
    return 0


def prune(stop_at, kept):
    """Removes the empty folders the package move leaves behind."""
    for root, dirs, files in os.walk(stop_at, topdown=False):
        if root == stop_at or root == kept or kept.startswith(root + os.sep):
            continue
        if not dirs and not files:
            os.rmdir(root)


def read_property(path, key):
    with io.open(path, encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if line.startswith(key + '='):
                return line[len(key) + 1:].strip()
    return ''


if __name__ == '__main__':
    sys.exit(main(sys.argv))
