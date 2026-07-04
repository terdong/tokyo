#!/bin/bash
# Get the current version from build.sbt
CURRENT_VERSION=$(grep -E 'ThisBuild\s*/\s*version\s*:=\s*".*"' build.sbt | sed -E 's/.*version\s*:=\s*"([^"]*)".*/\1/')

# Get the version from the main branch to see if we already bumped it in this PR session
if git show origin/main:build.sbt >/dev/null 2>&1; then
  BASE_VERSION=$(git show origin/main:build.sbt | grep -E 'ThisBuild\s*/\s*version\s*:=\s*".*"' | sed -E 's/.*version\s*:=\s*"([^"]*)".*/\1/')
elif git show main:build.sbt >/dev/null 2>&1; then
  BASE_VERSION=$(git show main:build.sbt | grep -E 'ThisBuild\s*/\s*version\s*:=\s*".*"' | sed -E 's/.*version\s*:=\s*"([^"]*)".*/\1/')
else
  BASE_VERSION=""
fi

# If we already bumped the version (CURRENT_VERSION > BASE_VERSION), don't bump again
if [ -n "$BASE_VERSION" ] && [ "$CURRENT_VERSION" != "$BASE_VERSION" ]; then
  echo "Version has already been bumped from $BASE_VERSION to $CURRENT_VERSION on this branch. Skipping bump."
  NEW_VERSION="$CURRENT_VERSION"
else
  # Increment the patch version (e.g., 0.1.2 -> 0.1.3)
  IFS='.' read -r major minor patch <<< "$CURRENT_VERSION"
  NEW_VERSION="$major.$minor.$((patch + 1))"

  # Update build.sbt with the newly incremented version
  sed -i -E "s|ThisBuild[[:space:]]*/[[:space:]]*version[[:space:]]*:=[[:space:]]*\"$CURRENT_VERSION\"|ThisBuild / version := \"$NEW_VERSION\"|g" build.sbt
  echo "Version bumped from $CURRENT_VERSION to $NEW_VERSION"
fi

# ----------------------------------------------------------------
# Automatically update specific lines in README.md
# ----------------------------------------------------------------
# 2. Update the lines containing "com.github.terdong.tokyo" with the NEW_VERSION
# Safely wrap variable references to avoid bash injection or edge-case syntax corruption
sed -i -E "s|(\"com.github.terdong.tokyo\" %{2,3} \"tokyo[^\"]*\" % \")[^\"]+(\")|\1${NEW_VERSION}\2|g" README.md

# 1. Dynamically parse the kyoVersion variable from build.sbt
KYO_VERSION=$(grep -E 'val\s+kyoVersion\s*=\s*".*"' build.sbt | sed -E 's/.*val\s+kyoVersion\s*=\s*"([^"]*)".*/\1/' | head -n 1)

# Fallback to a stable default version if parsing fails
if [ -z "$KYO_VERSION" ]; then
  KYO_VERSION="1.0.0-RC5"
fi

# 3. Update the Kyo version section (e.g., align with **Kyo 1.0.0-RC2**)
sed -i -E "s|(align with \*\*Kyo )[^*]+(\*\*)|\1${KYO_VERSION}\2|g" README.md

echo "README.md updated successfully: Tokyo version set to $NEW_VERSION, Kyo version set to $KYO_VERSION"