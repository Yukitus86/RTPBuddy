# -*- coding: utf-8 -*-
"""Copy RTPBuddy's built jar into a partner project's libs/ folder.

    python install-rtpbuddy-jar.py <project-dir>

The version is read from the partner project's own gradle.properties
(rtpbuddy_version), so this copies exactly the jar that project asks for and
says so if RTPBuddy has not been built at that version yet.
"""
from __future__ import print_function

import io
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RTPBUDDY_ROOT = os.path.dirname(HERE)


def read_property(path, key):
    with io.open(path, encoding='utf-8') as handle:
        for line in handle:
            line = line.strip()
            if line.startswith(key + '='):
                return line[len(key) + 1:].strip()
    return None


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2

    project = os.path.abspath(argv[1])
    properties = os.path.join(project, 'gradle.properties')
    if not os.path.isfile(properties):
        print('not a gradle project: %s' % project)
        return 1

    version = read_property(properties, 'rtpbuddy_version')
    if not version:
        print('no rtpbuddy_version in %s' % properties)
        return 1

    name = 'rtpbuddy-%s.jar' % version
    source = os.path.join(RTPBUDDY_ROOT, 'build', 'libs', name)
    if not os.path.isfile(source):
        print('RTPBuddy %s is not built.\n  expected: %s\n\n'
              'Build it first:\n'
              '  cd %s\n'
              '  gradlew build' % (version, source, RTPBUDDY_ROOT))
        return 1

    libs = os.path.join(project, 'libs')
    if not os.path.isdir(libs):
        os.makedirs(libs)

    # Old versions left lying about are the kind of thing that compiles fine and
    # then loads the wrong interface at runtime, so they go.
    for stale in os.listdir(libs):
        if stale.startswith('rtpbuddy-') and stale.endswith('.jar') and stale != name:
            os.remove(os.path.join(libs, stale))
            print('removed old %s' % stale)

    shutil.copy2(source, os.path.join(libs, name))
    print('copied %s -> %s' % (name, libs))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
