#!/bin/bash
# Get the current version from build.sbt
CURRENT_VERSION=$(grep -E 'ThisBuild\s*/\s*version\s*:=\s*".*"' build.sbt | sed -E 's/.*version\s*:=\s*"([^"]*)".*/\1/')

# Increment the patch version (e.g., 0.1.2 -> 0.1.3)
IFS='.' read -r major minor patch <<< "$CURRENT_VERSION"
NEW_VERSION="$major.$minor.$((patch + 1))"

# Update build.sbt
sed -i "s/ThisBuild \/ version := \"$CURRENT_VERSION\"/ThisBuild \/ version := \"$NEW_VERSION\"/" build.sbt